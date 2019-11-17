/**
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * <p>
 * http://aws.amazon.com/apache2.0
 * <p>
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazonaws.demo.androidpubsub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class PubSubActivity extends Activity {

    static final String LOG_TAG = PubSubActivity.class.getCanonicalName();


    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a1rgc1vd3jykuy-ats.iot.ap-northeast-2.amazonaws.com";
    private static final String AWS_IOT_POLICY_NAME = "FishStateAppPolicy2";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.AP_NORTHEAST_2;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";

    long mNow;
    Date mDate;
    SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
    long mNow2;
    Date mDate2;
    SimpleDateFormat mFormat2 = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
    TextView mTextView;
    TextView mTextView2;
    TextView TmpUpdateTime;
    TextView PHUpdateTime;
    TextView WLUpdateTime;
    Button mRefreshBtn;


    TextView TempState;
    TextView WaterlevelState;
    TextView PHState;
    TextView tvClientId;
    TextView tvStatus;
    TextView TmpGuideLight2;
    TextView PHGuideLight2;
    TextView WLGuideLight2;
    TextView TmpGuide1;
    TextView PHGuide1;
    TextView WLGuide1;
    TextView TmpGuide2;
    TextView PHGuide2;
    TextView WLGuide2;

    Button btnConnect;
    LineChart WL_lineChart;
    ArrayList<Entry> WL_entries;
    LineData WL_data;
    LineDataSet WL_dataset;
    XAxis WL_xAxis;

    LineChart PH_lineChart;
    ArrayList<Entry> PH_entries;
    LineData PH_data;
    LineDataSet PH_dataset;
    XAxis PH_xAxis;

    LineChart TMP_lineChart;
    ArrayList<Entry> TMP_entries;
    LineData TMP_data;
    LineDataSet TMP_dataset;
    XAxis TMP_xAxis;

    AWSIotClient mIotAndroidClient;
    AWSIotMqttManager mqttManager;
    String clientId;
    String keystorePath;
    String keystoreName;
    String keystorePassword;

    KeyStore clientKeyStore = null;
    String certificateId;

    private String getTime() {
        mNow = System.currentTimeMillis();
        mDate = new Date(mNow);
        return mFormat.format(mDate);
    }

    private String NextgetTime() {
        mNow2 = System.currentTimeMillis();
        mNow2 = mNow2 + (6 * 60 * 60 * 1000);
        mDate2 = new Date(mNow2);
        return mFormat2.format(mDate2);
    }

    public void Last_Feeding_Update(final View view) {
        switch (view.getId()) {
            case R.id.refreshBtn:
                mTextView.setText(getTime());
                mTextView2.setText(NextgetTime());
                break;
            default:
                break;
        }
    }

    public void connectClick(final View view) {
        Log.d(LOG_TAG, "clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(status.toString());
                            if (throwable != null) {
                                Log.e(LOG_TAG, "Connection error.", throwable);
                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
            tvStatus.setText("Error! " + e.getMessage());
        }
    }

    int subscribeCount = 1;
    int WL_Graph_count = 1;
    int PH_Graph_count = 1;
    int TMP_Graph_count = 1;

    public void subscribeClick(final View view) {
        String topic = "";

        if (subscribeCount == 3) {
            topic = "$aws/things/Saturday/shadow/update";
            subscribeCount = 1;
        } else if (subscribeCount == 2) {
            topic = "$aws/things/PH-Sensor/shadow/update";
            subscribeCount++;
        } else if (subscribeCount == 1) {
            topic = "$aws/things/WaterTemperature-Sensor/shadow/update";
            subscribeCount++;
        }


        Log.d(LOG_TAG, "topic = " + topic);

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String message = new String(data, "UTF-8");
                                        Log.d(LOG_TAG, "Message arrived:");
                                        Log.d(LOG_TAG, "   Topic: " + topic);
                                        Log.d(LOG_TAG, " Message: " + message);

                                        if (topic.equals("$aws/things/WaterTemperature-Sensor/shadow/update")) {
                                            String tmpmessage = new String(message);
                                            tmpmessage = tmpmessage.substring(31, 33);
                                            tmpmessage = tmpmessage.trim();
                                            Log.d(LOG_TAG, " Message: " + tmpmessage);
                                            TempState.setText(tmpmessage);
                                            int TempGuidelevel = Integer.parseInt(tmpmessage);
                                            TmpUpdateTime.setText(getTime());
                                            TMP_entries.add(new Entry(TMP_Graph_count, TempGuidelevel));
                                            TMP_xAxis.setAxisMaxValue(TMP_Graph_count);
                                            TMP_Graph_count++;
                                            TMP_lineChart.setData(TMP_data);
                                            TMP_lineChart.notifyDataSetChanged();
                                            TMP_lineChart.invalidate();


                                            if (TempGuidelevel <= 28 && TempGuidelevel >= 24) {
                                                TmpGuideLight2.setTextColor(Color.GREEN);
                                                TmpGuide1.setText("양호");
                                                TmpGuide1.setTextColor(Color.GREEN);
                                                TmpGuide2.setText("최적의 온도를 유지중 입니다.");
                                            }
                                            if (TempGuidelevel > 28 && TempGuidelevel <= 32 || TempGuidelevel < 24 && TempGuidelevel >= 20) {
                                                TmpGuideLight2.setTextColor(Color.YELLOW);
                                                TmpGuide1.setText("주의");
                                                TmpGuide1.setTextColor(Color.YELLOW);
                                                TmpGuide2.setText("간접적인 온도 조절 필요 (온도 조절기를 동작하십시오)");
                                            }
                                            if (TempGuidelevel > 32 || TempGuidelevel < 20) {
                                                TmpGuideLight2.setTextColor(Color.RED);
                                                TmpGuide1.setText("위험");
                                                TmpGuide1.setTextColor(Color.RED);
                                                TmpGuide2.setText("반려어의 생명에 지장이 있을 수 있습니다.\n온도에 대한 즉각적인 조치가 필요합니다.");
                                            }
                                        }
                                        if (topic.equals("$aws/things/PH-Sensor/shadow/update")) {
                                            String phmessage = new String(message);
                                            phmessage = phmessage.substring(31, 35);
                                            phmessage = phmessage.trim();
                                            Log.d(LOG_TAG, " Message: " + phmessage);
                                            PHState.setText(phmessage);
                                            Float PHGuidelevel = Float.parseFloat(phmessage);
                                            PHUpdateTime.setText(getTime());
                                            PH_entries.add(new Entry(PH_Graph_count, PHGuidelevel));
                                            PH_xAxis.setAxisMaxValue(PH_Graph_count);
                                            PH_Graph_count++;
                                            PH_lineChart.setData(PH_data);
                                            PH_lineChart.notifyDataSetChanged();
                                            PH_lineChart.invalidate();


                                            if (PHGuidelevel <= 9 && PHGuidelevel >= 7) {
                                                PHGuideLight2.setTextColor(Color.GREEN);
                                                PHGuide1.setText("양호");
                                                PHGuide1.setTextColor(Color.GREEN);
                                                PHGuide2.setText("약산성을 유지중 입니다.");
                                            }
                                            if (PHGuidelevel > 9 && PHGuidelevel <= 10 || PHGuidelevel < 7 && PHGuidelevel >= 5) {
                                                PHGuideLight2.setTextColor(Color.YELLOW);
                                                PHGuide1.setText("주의");
                                                PHGuide1.setTextColor(Color.YELLOW);
                                                PHGuide2.setText("높은 산성(알칼리)를 띄고 있습니다.\n여과기등을 작동시켜야 합니다.");
                                            }
                                            if (PHGuidelevel > 10 || PHGuidelevel < 5) {
                                                PHGuideLight2.setTextColor(Color.RED);
                                                PHGuide1.setText("위험");
                                                PHGuide1.setTextColor(Color.RED);
                                                PHGuide2.setText("매우 높은 산성(알칼리)를 띄고 있습니다.\n즉시 농도조절물을 투여하십시오.");
                                            }
                                        }
                                        if (topic.equals("$aws/things/Saturday/shadow/update")) {
                                            String wlmessage = new String(message);

                                            wlmessage = wlmessage.substring(31, 33);
                                            wlmessage = wlmessage.trim();
                                            Log.d(LOG_TAG, " Message: " + wlmessage);
                                            WaterlevelState.setText(wlmessage);
                                            int WLGuidelevel = Integer.parseInt(wlmessage);
                                            WLUpdateTime.setText(getTime());
                                            WL_entries.add(new Entry(WL_Graph_count, WLGuidelevel));
                                            WL_xAxis.setAxisMaxValue(WL_Graph_count);
                                            WL_Graph_count++;
                                            WL_lineChart.setData(WL_data);
                                            WL_lineChart.notifyDataSetChanged();
                                            WL_lineChart.invalidate();

                                            if (WLGuidelevel > 90) {
                                                WLGuideLight2.setTextColor(Color.GREEN);
                                                WLGuide1.setText("양호");
                                                WLGuide1.setTextColor(Color.GREEN);
                                                WLGuide2.setText("적정 수위를 유지중입니다.");
                                            }

                                            if (WLGuidelevel <= 90 && WLGuidelevel >= 85) {
                                                WLGuideLight2.setTextColor(Color.YELLOW);
                                                WLGuide1.setText("주의");
                                                WLGuide1.setTextColor(Color.YELLOW);
                                                WLGuide2.setText("수위가 낮습니다.\n수조의 물보충이 필요합니다.");
                                            }

                                            if (WLGuidelevel < 85 && WLGuidelevel >= 0) {
                                                WLGuideLight2.setTextColor(Color.RED);
                                                WLGuide1.setText("위험");
                                                WLGuide1.setTextColor(Color.RED);
                                                WLGuide2.setText("수위가 너무 낮습니다. 농도에 영향을 줄 수 있습니다. 즉시 물보충을 하십시오.");
                                            }
                                        }
                                        if (topic.equals("$aws/things/Saturday/shadow/update")) {
                                            String testmessage = new String(message);

                                            testmessage = testmessage.substring(31, 33);
                                            Log.d(LOG_TAG, " Message: " + testmessage);
                                        }

                                    } catch (UnsupportedEncodingException e) {
                                        Log.e(LOG_TAG, "Message encoding error.", e);
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
    }


    public void publishClick(final View view) {
        final String topic = "$aws/things/Camera-Sensor/shadow/update";
        final String msg = "{\"state\":{\"desired\":{\"motor\":1}},\"clientToken\":\"Feeder\"}";

        try {
            mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
        Last_Feeding_Update(view);
    }


    public void disconnectClick(final View view) {
        try {
            mqttManager.disconnect();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Disconnect error.", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);


        mTextView = (TextView) findViewById(R.id.textView);
        mTextView2 = (TextView) findViewById(R.id.textView2);
        mRefreshBtn = (Button) findViewById(R.id.refreshBtn);
        TempState = findViewById(R.id.TempState);
        WaterlevelState = findViewById(R.id.WaterlevelState);
        PHState = findViewById(R.id.PHState);
        TmpGuideLight2 = findViewById(R.id.TmpGuideLight2);
        PHGuideLight2 = findViewById(R.id.PHGuideLight2);
        WLGuideLight2 = findViewById(R.id.WLGuideLight2);
        TmpGuide1 = findViewById(R.id.TmpGuide1);
        PHGuide1 = findViewById(R.id.PHGuide1);
        WLGuide1 = findViewById(R.id.WLGuide1);
        TmpGuide2 = findViewById(R.id.TmpGuide2);
        PHGuide2 = findViewById(R.id.PHGuide2);
        WLGuide2 = findViewById(R.id.WLGuide2);
        TmpUpdateTime = findViewById(R.id.TmpUpdateTime);
        PHUpdateTime = findViewById(R.id.PHUpdateTime);
        WLUpdateTime = findViewById(R.id.WLUpdateTime);


        tvClientId = findViewById(R.id.tvClientId);
        tvStatus = findViewById(R.id.tvStatus);

        btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setEnabled(false);

        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString();
        tvClientId.setText(clientId);

        // Initialize the AWS Cognito credentials provider
        AWSMobileClient.getInstance().initialize(this, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                initIoTClient();
            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG, "onError: ", e);
            }
        });

        WL_lineChart = (LineChart) findViewById(R.id.WL_chart);
        WL_entries = new ArrayList<>();
        WL_entries.add(new Entry(0, 0));

        WL_xAxis = WL_lineChart.getXAxis();
        WL_xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        WL_xAxis.setTextColor(Color.BLACK);
        WL_xAxis.setAxisMaxValue(WL_Graph_count);
        WL_xAxis.setAxisMinValue(0);
        YAxis WL_yLAxis = WL_lineChart.getAxisLeft();
        WL_yLAxis.setAxisMaxValue(100);
        WL_yLAxis.setAxisMinValue(0);
        WL_yLAxis.setTextColor(Color.BLACK);
        YAxis WL_yRAxis = WL_lineChart.getAxisRight();
        WL_yRAxis.setDrawLabels(false);
        WL_yRAxis.setDrawAxisLine(false);
        WL_yRAxis.setDrawGridLines(false);

        WL_dataset = new LineDataSet(WL_entries, "Water Level");
        WL_dataset.setLineWidth(2);

        WL_data = new LineData(WL_dataset);
        WL_dataset.setColors(Color.BLUE);
        WL_dataset.setDrawFilled(true);

        WL_lineChart.setData(WL_data);
        WL_lineChart.animateY(5000);
        WL_lineChart.invalidate();

        PH_lineChart = (LineChart) findViewById(R.id.PH_chart);
        PH_entries = new ArrayList<>();
        PH_entries.add(new Entry(0, 0));

        PH_xAxis = PH_lineChart.getXAxis();
        PH_xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        PH_xAxis.setTextColor(Color.BLACK);
        PH_xAxis.setAxisMaxValue(PH_Graph_count);
        PH_xAxis.setAxisMinValue(0);
        YAxis PH_yLAxis = PH_lineChart.getAxisLeft();
        PH_yLAxis.setAxisMaxValue(15);
        PH_yLAxis.setAxisMinValue(0);
        PH_yLAxis.setTextColor(Color.BLACK);
        YAxis PH_yRAxis = PH_lineChart.getAxisRight();
        PH_yRAxis.setDrawLabels(false);
        PH_yRAxis.setDrawAxisLine(false);
        PH_yRAxis.setDrawGridLines(false);

        PH_dataset = new LineDataSet(PH_entries, "PH Level");
        PH_dataset.setLineWidth(2);

        PH_data = new LineData(PH_dataset);
        PH_dataset.setColors(Color.MAGENTA);
        PH_dataset.setDrawFilled(true);

        PH_lineChart.setData(PH_data);
        PH_lineChart.animateY(5000);
        PH_lineChart.invalidate();

        TMP_lineChart = (LineChart) findViewById(R.id.TMP_chart);
        TMP_entries = new ArrayList<>();
        TMP_entries.add(new Entry(0, 0));

        TMP_xAxis = TMP_lineChart.getXAxis();
        TMP_xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        TMP_xAxis.setTextColor(Color.BLACK);
        TMP_xAxis.setAxisMaxValue(PH_Graph_count);
        TMP_xAxis.setAxisMinValue(0);
        YAxis TMP_yLAxis = TMP_lineChart.getAxisLeft();
        TMP_yLAxis.setAxisMaxValue(45);
        TMP_yLAxis.setAxisMinValue(0);
        TMP_yLAxis.setTextColor(Color.BLACK);
        YAxis TMP_yRAxis = TMP_lineChart.getAxisRight();
        TMP_yRAxis.setDrawLabels(false);
        TMP_yRAxis.setDrawAxisLine(false);
        TMP_yRAxis.setDrawGridLines(false);

        TMP_dataset = new LineDataSet(TMP_entries, "Temperature");
        TMP_dataset.setLineWidth(2);

        TMP_data = new LineData(TMP_dataset);
        TMP_dataset.setColors(Color.RED);
        TMP_dataset.setDrawFilled(true);

        TMP_lineChart.setData(TMP_data);
        TMP_lineChart.animateY(5000);
        TMP_lineChart.invalidate();
    }

    void initIoTClient() {
        Region region = Region.getRegion(MY_REGION);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
        mIotAndroidClient.setRegion(region);

        keystorePath = getFilesDir().getPath();
        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                    /* initIoTClient is invoked from the callback passed during AWSMobileClient initialization.
                    The callback is executed on a background thread so UI update must be moved to run on UI Thread. */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnConnect.setEnabled(true);
                        }
                    });
                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(LOG_TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnConnect.setEnabled(true);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(LOG_TAG,
                                "Exception occurred when generating new private key and certificate.",
                                e);
                    }
                }
            }).start();
        }
    }

}
