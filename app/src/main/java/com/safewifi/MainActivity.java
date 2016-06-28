package com.safewifi;


import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.safewifi.common.APInfo;
import com.safewifi.common.ARPTable;
import com.safewifi.common.Command;
import com.safewifi.common.ConnectWifi;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by JiseokWoo
 * MainActivity
 */
public class MainActivity extends Activity {

    private static final String get_url = "http://172.20.10.8/wifiscan.php";
    private static final String put_url = "http://172.20.10.8/wificonn.php";

    private APInfoAdapter apInfoAdapter;
    private WifiManager wifiManager;
    private WifiInfo wifiInfo;
    private WifiReceiver wifiReceiver;
    private List<ScanResult> scanResultList;
    private List<APInfo> apInfoList;
    private ListView listView;
    private ImageButton imageButton;
    private APInfo curAP;

    private ProgressDialog pbScan;
    private ProgressDialog pbCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 어플 아이콘, 타이틀, 새로고침 버튼
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        View customActionView = LayoutInflater.from(this).inflate(R.layout.action_bar, null);
        TextView tv_title = (TextView) customActionView.findViewById(R.id.tv_title);
        tv_title.setTypeface(Typeface.createFromAsset(getAssets(), "DroidSansFallback.ttf"));
        actionBar.setCustomView(customActionView);
        actionBar.setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
        // APInfo 객체가 저장될 리스트
        apInfoList = new ArrayList<>();

        // 리스트뷰를 위한 adapter 생성
        apInfoAdapter = new APInfoAdapter(getApplicationContext(), R.layout.row, apInfoList);

        listView = (ListView) findViewById(R.id.lv_aplist);
        listView.setOnItemClickListener(onItemClickListener);
        listView.setAdapter(apInfoAdapter);

        // wifi manager 생성
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiInfo = wifiManager.getConnectionInfo();

        wifiReceiver = new WifiReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(wifiReceiver, intentFilter);

        // ;새로고침 버튼 클릭, 스캔 다시 시작
        imageButton = (ImageButton) findViewById(R.id.scan_refresh);
        imageButton.setOnClickListener(new ImageButton.OnClickListener(){
            @Override
            public void onClick(View v){
                new ScanAP().execute();
            }
        });

        // 현재 AP 연결중일 경우
        if (wifiInfo.getBSSID() != null) {
           new CheckAP().execute();    // 현재 AP 정보 수집후 서버에 전송
        } else {
            new ScanAP().execute();
        }

    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            curAP = apInfoList.get(position);

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.info, null);


            TextView security = (TextView) layout.findViewById(R.id.tv_security);
            TextView info = (TextView) layout.findViewById(R.id.tv_info);

            if (curAP.getSecureLevel() != null && curAP.getConnCount() > 0) {
                if (curAP.getSecureLevel().equals(Command.SECURE_LEVEL_HIGH)) {
                    security.setText("      안전한 WiFi입니다. (" + curAP.getSecureScore() + "점)\n");
                } else if (curAP.getSecureLevel().equals(Command.SECURE_LEVEL_MEDIUM)) {
                    security.setText("      해킹 위협이 존재하는 WiFi. (" + curAP.getSecureScore() + "점)\n      안전한 WiFi 사용을 권장합니다.\n");
                } else if (curAP.getSecureLevel().equals(Command.SECURE_LEVEL_LOW)) {
                    security.setText("      보안에 취약한 WiFi입니다. (" + curAP.getSecureScore() + "점) \n      안전한 WiFi 사용을 권장합니다.\n");
                }

                String secure_info = "";

                if (curAP.getInfoEncrypt().contains(Command.ENCRYPT_OPEN)) {
                    secure_info += "        - 공유기 암호화 설정 안됨";
                } else if (curAP.getInfoEncrypt().contains(Command.ENCRYPT_WEP) || (curAP.getInfoEncrypt().contains(Command.ENCRYPT_WPA) && !curAP.getInfoEncrypt().contains(Command.ENCRYPT_WPA2))) {
                    secure_info += "        - 공유기 암호화 설정 취약";
                }

                if (curAP.getInfoDns() > 0) secure_info += "        - DNS 변조 의심 : (" + curAP.getInfoDns() + "건 탐지)";
                if (curAP.getInfoArp()) secure_info += "        - ARP 테이블 변조 의심";
                if (curAP.getInfoPort()) secure_info += "        - 비정상 포트 오픈";

                info.setText(secure_info);

            }


            else {
                security.setText("      보안정보를 알 수 없습니다.");
            }

            AlertDialog.Builder adBuilder = new AlertDialog.Builder(MainActivity.this);
            adBuilder.setCustomTitle( setFont(curAP.getSSID() + "의 정보"));
            adBuilder.setView(layout);



            adBuilder.setPositiveButton("연결", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (scanResultList != null) {
                        final ScanResult ap = scanResultList.get(curAP.getPosition());

                        if (ap.capabilities.contains(Command.ENCRYPT_OPEN)) {
                            if (ConnectWifi.connect(wifiManager, ap, null)) {
                                // TODO: AP 연결후 처리?
                            } else {
                                errorDialog("연결 실패", ap.SSID + "에 연결하지 못했습니다.", "확인");
                            }
                        } else {

                            WifiConfiguration config = ConnectWifi.findStoredConfig(wifiManager, ap);

                            if (config == null) {
                                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                                View layout = inflater.inflate(R.layout.connect, null);
                                final EditText et_password = (EditText) layout.findViewById(R.id.et_password);
                                et_password.setPrivateImeOptions("defaultInputmode=english;");

                                AlertDialog.Builder adBuilder = new AlertDialog.Builder(MainActivity.this);
                                adBuilder.setCustomTitle(setFont(curAP.getSSID() + "에 연결"));
                                adBuilder.setView(layout);

                                adBuilder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                        String password = et_password.getText().toString();

                                        if (ConnectWifi.connect(wifiManager, ap, password)) {
                                            // TODO: AP 연결후 처리?
                                        } else {
                                            errorDialog("연결 실패", ap.SSID + "에 연결하지 못했습니다.", "확인");
                                        }

                                    }
                                });

                                adBuilder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });

                                AlertDialog alertDialog = adBuilder.create();
                                alertDialog.show();
                            } else {
                                ConnectWifi.connect(wifiManager, config);
                            }
                        }
                    } else {
                        errorDialog("AP 정보 확인 에러", "AP 정보를 확인할 수 없습니다.\n새로고침 후 다시 시도해주세요.", "확인");
                    }
                }
            });

            adBuilder.setNegativeButton("취소", null);
            AlertDialog alertDialog = adBuilder.create();
            alertDialog.show();
            int titleDividerId = getResources().getIdentifier("titleDivider", "id", "android");
            View titleDivider = alertDialog.findViewById(titleDividerId);
            if (titleDivider != null)
                titleDivider.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    };

    private void errorDialog(String title, String msg, String button) {
        AlertDialog.Builder adBuilder = new AlertDialog.Builder(getApplicationContext());
        adBuilder.setCustomTitle(setFont(title));
        setTitle(title);
        adBuilder.setMessage(msg);
        adBuilder.setNeutralButton(button, null);
        AlertDialog alertDialog = adBuilder.create();

        alertDialog.show();
    }

    private TextView setFont(String content) {
        TextView tv_title = new TextView(getApplicationContext());
        tv_title.setText(content);
        tv_title.setTypeface(Typeface.createFromAsset(getAssets(), "DroidSansFallback.ttf"));
        tv_title.setTextColor(Color.parseColor("#ef3636"));
        tv_title.setTextSize(20);
        tv_title.setPadding(40,40,40,40);


        return tv_title;
    }

    public class WifiReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                int error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);

                if(error == WifiManager.ERROR_AUTHENTICATING) {
                    Toast.makeText(context, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                    ScanResult ap = scanResultList.get(curAP.getPosition());
                    WifiConfiguration wifiConfiguration = ConnectWifi.findStoredConfig(wifiManager, ap);

                    if (wifiConfiguration != null) {
                        wifiManager.removeNetwork(wifiConfiguration.networkId);
                    }
                }

                switch(state) {
                    case ASSOCIATED:
                        break;
                    case ASSOCIATING:
                        break;
                    case AUTHENTICATING:
                        Toast.makeText(context, "인증을 진행하고 있습니다.", Toast.LENGTH_SHORT).show();
                        break;
                    case COMPLETED:
                        //Toast.makeText(context, "연결되었습니다.", Toast.LENGTH_SHORT).show();
                        break;
                    case DISCONNECTED:
                        break;
                    case DORMANT:
                        break;
                    case FOUR_WAY_HANDSHAKE:
                        Toast.makeText(context, "연결중입니다.", Toast.LENGTH_SHORT).show();
                        break;
                    case GROUP_HANDSHAKE:
                        break;
                    case INACTIVE:
                        break;
                    case INTERFACE_DISABLED:
                        break;
                    case INVALID:
                        break;
                    case SCANNING:
                        break;
                    case UNINITIALIZED:
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * APInfo 클래스와 리스트뷰를 연결해주는 Adapter
     */
    private class APInfoAdapter extends ArrayAdapter<APInfo> {

        public APInfoAdapter(Context context, int textViewResourceId, List<APInfo> apInfoList) {
            super(context, textViewResourceId, apInfoList);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ViewHolder viewHolder;

            if (view == null) {
                view = layoutInflater.inflate(R.layout.row, null);
                viewHolder = new ViewHolder();
                viewHolder.iv_security = (ImageView) view.findViewById(R.id.iv_security);
                viewHolder.tv_ssid = (TextView) view.findViewById(R.id.tv_ssid);
                viewHolder.iv_signal = (ImageView) view.findViewById(R.id.iv_signal);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            new DrawView().execute(position, viewHolder);

            /*APInfo apInfo = apInfoList.get(position);
            String secure_level = apInfo.getSecureLevel();
            Integer signal_level = apInfo.getSignalLevel();

            // 보안도 정보 UI 표시
            if (viewHolder.iv_security != null && secure_level != null) {
                if (secure_level.equals(Command.SECURE_LEVEL_HIGH)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_h);
                } else if (secure_level.equals(Command.SECURE_LEVEL_MEDIUM)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_m);
                } else if (secure_level.equals(Command.SECURE_LEVEL_LOW)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_l);
                }
            }

            // SSID 표시
            if (viewHolder.tv_ssid != null && apInfo.getSSID() != null) {
                viewHolder.tv_ssid.setText(apInfo.getSSID());
            }
            viewHolder.tv_ssid.setTypeface(Typeface.createFromAsset(getAssets(), "DroidSansFallback.ttf"));

            // 신호 강도 정보 UI 표시
            if (viewHolder.iv_signal != null && signal_level != null) {
                if (signal_level > -50) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_excellent);
                } else if (signal_level > -70) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_good);
                } else if (signal_level > -90) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_fair);
                } else if (signal_level <= -90) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_poor);
                }
            }*/

            return view;
        }
    }

    private class ViewHolder {
        ImageView iv_security;
        TextView tv_ssid;
        ImageView iv_signal;
    }

    private class DrawView extends AsyncTask<Object, Integer, ViewHolder> {
        APInfo apInfo;
        ViewHolder viewHolder;

        @Override
        protected ViewHolder doInBackground(Object... params) {
            int position = (int) params[0];
            viewHolder = (ViewHolder) params[1];
            apInfo = apInfoList.get(position);

            if (apInfo == null) {
                cancel(true);
            }

            return viewHolder;
        }

        @Override
        protected void onPostExecute(ViewHolder viewHolder) {

            String secure_level = apInfo.getSecureLevel();
            Integer signal_level = apInfo.getSignalLevel();

            // 보안도 정보 UI 표시
            if (viewHolder.iv_security != null && secure_level != null) {
                if (secure_level.equals(Command.SECURE_LEVEL_HIGH)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_h);
                } else if (secure_level.equals(Command.SECURE_LEVEL_MEDIUM)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_m);
                } else if (secure_level.equals(Command.SECURE_LEVEL_LOW)) {
                    viewHolder.iv_security.setImageResource(R.mipmap.secure_level_l);
                }
            }

            // SSID 표시
            if (viewHolder.tv_ssid != null && apInfo.getSSID() != null) {
                viewHolder.tv_ssid.setText(apInfo.getSSID());
            }
            viewHolder.tv_ssid.setTypeface(Typeface.createFromAsset(getAssets(), "DroidSansFallback.ttf"));

            // 신호 강도 정보 UI 표시
            if (viewHolder.iv_signal != null && signal_level != null) {
                if (signal_level > -50) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_excellent);
                } else if (signal_level > -70) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_good);
                } else if (signal_level > -90) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_fair);
                } else if (signal_level <= -90) {
                    viewHolder.iv_signal.setImageResource(R.mipmap.signal_poor);
                }
            }

        }
    }

    /**
     * APInfo의 SignalLevel을 기준으로 정렬하기 위한 Comparator
     */
    static class LevelAscCompare implements Comparator<APInfo> {

        @Override
        public int compare(APInfo ap1, APInfo ap2) {
            return ap2.getSignalLevel().compareTo(ap1.getSignalLevel());
        }
    }

    /**
     * 주변 AP를 검색해 MAC 기반으로 서버에 정보 조회
     */
    private class ScanAP extends AsyncTask<String, Integer, String> {

        @Override
        protected  void onPreExecute() {
            pbScan = ProgressDialog.show(MainActivity.this, "", "스캔중입니다. 잠시만 기다려주세요.");

            apInfoAdapter.clear();
            apInfoList.clear();

            // 와이파이 비활성일 경우 활성화
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }
        }

        @Override
        protected String doInBackground(String... params) {
            // TODO : 단말의 Wifi가 꺼져있을 경우 Wifi 검색이 되지 않음.

            // 와이파이 리스트 스캔
            if (wifiManager.isWifiEnabled() && wifiManager.startScan()) {
                scanResultList = wifiManager.getScanResults();

                // 스캔 결과 adapter에 추가
                if (scanResultList != null && !scanResultList.isEmpty()) {
                    for (ScanResult ap : scanResultList) {
                        if (ap.SSID.equals("")) continue;
                        if (ap.level < -88) continue;

                        APInfo apInfo = null;
                        try {
                            apInfo = getAPInfo(ap.BSSID, ap.SSID, ap.level, ap.capabilities);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        if (apInfo != null){
                            apInfoList.add(apInfo);
                        } else {
                            // TODO: 에러 처리
                        }
                    }
                } else {
                    return Command.WIFI_UNAVAILABLE_ERROR;
                }
            } else {
                return Command.WIFI_ENABLE_ERROR;
            }
            return Command.SUCCESS;
        }

        @Override
        protected void onPostExecute(String result) {
            Collections.sort(apInfoList, new LevelAscCompare());
            pbScan.dismiss();
            apInfoAdapter.notifyDataSetChanged();
            super.onPostExecute(result);
        }
    }

    /**
     * 접속중인 AP 정보를 체크해 서버로 전송
     */
    private class CheckAP extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPreExecute() {
            pbCheck = ProgressDialog.show(MainActivity.this, "", "정보 업로드중입니다. 잠시만 기다려주세요.");
            wifiManager.startScan();
            scanResultList = wifiManager.getScanResults();
        }

        @Override
        protected String doInBackground(String... params) {
            if (wifiManager != null && wifiInfo != null) {
                DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                String encrypt = null;

                for (ScanResult ap : scanResultList) {
                    if (ap.BSSID.equals(wifiInfo.getBSSID())) {
                        encrypt = ap.capabilities;
                        break;
                    }
                }

                APInfo apInfo = new APInfo(wifiInfo.getBSSID(), wifiInfo.getSSID(), wifiInfo.getRssi(), encrypt, apInfoList.size());
                apInfo.setDHCP(dhcpInfo);
                apInfo.setInfoArp(ARPTable.checkARPSpoof());

                // TODO: 테스트용
                apInfo.setInfoPort(true);

                // 서버에 현재 AP 정보 업로드
                putAPInfo(apInfo);

                return Command.SUCCESS;
            }
            return Command.FAIL;
        }

        @Override
        protected void onPostExecute(String result) {
            pbCheck.dismiss();
            new ScanAP().execute();
            super.onPostExecute(result);
        }
    }

    /**
     * 서버로부터 AP 정보 조회
     * @param mac
     * @param ssid
     * @return
     * @throws JSONException
     */
    private APInfo getAPInfo(String mac, String ssid, Integer signal, String encrypt) throws JSONException {
        APInfo apInfo = new APInfo(mac, ssid, signal, encrypt, apInfoList.size());

        try {
            // wifiscan connection 생성
            URL url = new URL(get_url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // connection post 설정
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            // post로 데이터 전송
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"), true);
            pw.write(apInfo.toString(Command.GET));
            pw.flush();

            // 서버로부터 response 수신
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String response = bf.readLine();

                // TODO: 에러 처리 로직 개선 필요
                if (response.equals(Command.NO_MAC_ERROR)) {
                    return null;
                } else if (response.equals(Command.DB_INSERT_ERROR) || response.equals(Command.DB_SELECT_ERROR)) {
                    return null;
                } else if (response.equals(Command.EMPTY)) {
                    return null;
                } else {
                    apInfo.setDBInfo(response);
                    apInfo.setInfoEncrypt(encrypt);
                }
            }

            conn.disconnect();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return apInfo;
    }

    /**
     * 서버로 AP 정보 업로드
     * @param apInfo
     */
    private String putAPInfo(APInfo apInfo) {
        String response = Command.EMPTY;

        try {
            // wifiscan connection 생성
            URL url = new URL(put_url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // connection post 설정
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            // post로 데이터 전송
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"), true);
            pw.write(apInfo.toString(Command.PUT));
            pw.flush();

            // 서버로부터 response 수신
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                response = bf.readLine();
            }

            conn.disconnect();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

}