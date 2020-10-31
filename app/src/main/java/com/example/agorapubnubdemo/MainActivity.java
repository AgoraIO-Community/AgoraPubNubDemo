package com.example.agorapubnubdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agorapubnubdemo.messageui.MessageAdapter;
import com.example.agorapubnubdemo.model.MessageBean;
import com.google.gson.JsonObject;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNStatusCategory;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.pubnub.api.models.consumer.pubsub.PNSignalResult;
import com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult;
import com.pubnub.api.models.consumer.pubsub.message_actions.PNMessageActionResult;
import com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult;
import com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult;
import com.pubnub.api.models.consumer.pubsub.objects.PNUserResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.models.UserInfo;
import io.agora.rtc.video.VideoCanvas;

public class MainActivity extends AppCompatActivity {
    final String TAG = "MainActivity";
    PNConfiguration pnConfiguration;

    PubNub pubnub;
    String channelName = "test";
    EditText editText;

    private static final int PERMISSION_REQ_ID = 22;
    private RtcEngine mRtcEngine;
    private RelativeLayout mRemoteContainer;
    private SurfaceView mRemoteView;
    private FrameLayout mLocalContainer;
    private SurfaceView mLocalView;
    private ImageView mMuteBtn;
    private LinearLayout mChatLayout;
    private LinearLayout mMessageSendLayout;
    private boolean isCalling = true;
    private boolean isMuted = false;
    private RecyclerView mRecyclerView;
    private MessageAdapter mMessageAdapter;
    private List<MessageBean> mMessageBeanList = new ArrayList<>();

    private boolean isLive = false;
    private boolean isRemoteLive = false;
    private boolean isAbleToSendMessage = false;

    // Ask for Android device permissions at runtime.
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private IRtcEngineEventHandler mRtcEngineEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
            super.onJoinChannelSuccess(channel, uid, elapsed);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pnConfiguration.setUuid(String.valueOf(uid));
                    Toast.makeText(MainActivity.this, "Join channel successfully!" + uid, Toast.LENGTH_SHORT).show();
                    isLive = true;
                    checkIfAbleToSendMessage();
                }
            });
        }

        @Override
        public void onRemoteVideoStateChanged(final int uid, int state, int reason, int elapsed) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
            if (state == Constants.REMOTE_VIDEO_STATE_STARTING) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setUpRemoteView(uid);
                        isRemoteLive = true;
                        checkIfAbleToSendMessage();
                    }
                });
            }
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            super.onUserOffline(uid, reason);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    removeRemoteView();
                    isRemoteLive = false;
                    checkIfAbleToSendMessage();
                    emptyChat();
                }
            });
        }

        @Override
        public void onUserInfoUpdated(int uid, final UserInfo userInfo) {
            super.onUserInfoUpdated(uid, userInfo);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "new User info:" + userInfo.userAccount + " + " + userInfo.uid, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(int err) {
            super.onError(err);

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_chat_view);

        initUI();
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
            initEngineAndJoinChannel();
            initPubNubClient();
        }
    }

    private void initPubNubClient() {
        pnConfiguration = new PNConfiguration();
        pnConfiguration.setSubscribeKey(getString(R.string.pubNub_sub_key));
        pnConfiguration.setPublishKey(getString(R.string.pubNub_pub_key));

        pubnub = new PubNub(pnConfiguration);

        pubnub.addListener(new SubscribeCallback() {
            @Override
            public void status(PubNub pubnub, PNStatus status) {


                if (status.getCategory() == PNStatusCategory.PNUnexpectedDisconnectCategory) {
                    // This event happens when radio / connectivity is lost
                } else if (status.getCategory() == PNStatusCategory.PNConnectedCategory) {

                    // Connect event. You can do stuff like publish, and know you'll get it.
                    // Or just use the connected event to confirm you are subscribed for
                    // UI / internal notifications, etc

                    if (status.getCategory() == PNStatusCategory.PNConnectedCategory) {

                    }
                } else if (status.getCategory() == PNStatusCategory.PNReconnectedCategory) {

                    // Happens as part of our regular operation. This event happens when
                    // radio / connectivity is lost, then regained.
                } else if (status.getCategory() == PNStatusCategory.PNDecryptionErrorCategory) {

                    // Handle messsage decryption error. Probably client configured to
                    // encrypt messages and on live data feed it received plain text.
                }
            }
            @Override
            public void message(PubNub pubnub, final PNMessageResult message) {
                // Handle new message stored in message.message
                if (message.getChannel() != null) {
                    // Message has been received on channel group stored in
                    // message.getChannel()
                }
                else {
                    // Message has been received on channel stored in
                    // message.getSubscription()
                }
                Log.d(TAG, "publisher: " + message.getPublisher());
                // extract desired parts of the payload, using Gson
                final String msg = message.getMessage().getAsJsonObject().get("msg").getAsString();
                if (!message.getPublisher().equals(pnConfiguration.getUuid())) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String account = message.getPublisher();
                            Log.i(TAG, "onMessageReceived account = " + account + " msg = " + msg);
                            MessageBean messageBean = new MessageBean(account, msg, false);
                            messageBean.setBackground(R.drawable.shape_circle_blue);
                            mMessageBeanList.add(messageBean);
                            mMessageAdapter.notifyItemRangeChanged(mMessageBeanList.size(), 1);
                            mRecyclerView.scrollToPosition(mMessageBeanList.size() - 1);
                        }
                    });
                }

            /*
                log the following items with your favorite logger
                    - message.getMessage()
                    - message.getSubscription()
                    - message.getTimetoken()
            */
            }

            @Override
            public void presence(PubNub pubnub, PNPresenceEventResult presence) {

            }

            @Override
            public void signal(PubNub pubnub, PNSignalResult pnSignalResult) {

            }

            @Override
            public void user(PubNub pubnub, PNUserResult pnUserResult) {

            }

            @Override
            public void space(PubNub pubnub, PNSpaceResult pnSpaceResult) {

            }

            @Override
            public void membership(PubNub pubnub, PNMembershipResult pnMembershipResult) {

            }

            @Override
            public void messageAction(PubNub pubnub, PNMessageActionResult pnMessageActionResult) {

            }

            @Override
            public void file(PubNub pubnub, PNFileEventResult pnFileEventResult) {

            }
        });
        pubnub.subscribe().channels(Arrays.asList(channelName)).execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQ_ID: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    break;
                }
                initEngineAndJoinChannel();
                initPubNubClient();
                break;
            }
        }
    }

    private void initUI() {
        mChatLayout = findViewById(R.id.chat_layout);
        mMessageSendLayout = findViewById(R.id.message_send_layout);
        mLocalContainer = findViewById(R.id.local_video_view_container);
        mRemoteContainer = findViewById(R.id.remote_video_view_container);
        mMuteBtn = findViewById(R.id.btn_mute);
        editText = findViewById(R.id.message_edittiext);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        mMessageAdapter = new MessageAdapter(this, mMessageBeanList);
        mRecyclerView = findViewById(R.id.message_list_audience);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mMessageAdapter);

        isLive = false;
        isRemoteLive = false;
        isAbleToSendMessage = false;
    }

    private void initEngineAndJoinChannel() {
        initializeEngine();
        setUpLocalView();
        joinChannel();
    }

    private void initializeEngine() {
        try {
            mRtcEngine = RtcEngine.create(getBaseContext(), getString(R.string.agora_app_id), mRtcEngineEventHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUpLocalView() {
        mRtcEngine.enableVideo();
        mLocalView = RtcEngine.CreateRendererView(getBaseContext());
        mLocalContainer.addView(mLocalView);
        mLocalView.setZOrderMediaOverlay(true);

        VideoCanvas localVideoCanvas = new VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0);
        mRtcEngine.setupLocalVideo(localVideoCanvas);
    }

    private void setUpRemoteView(int uid) {
        mRemoteView = RtcEngine.CreateRendererView(getBaseContext());
        mRemoteContainer.addView(mRemoteView);
        VideoCanvas remoteVideoCanvas = new VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid);
        mRtcEngine.setupRemoteVideo(remoteVideoCanvas);
    }

    private void joinChannel() {
        String token = getString(R.string.agora_token);
        mRtcEngine.joinChannel(token, channelName, "", 0);
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }
        return true;
    }

    public void onCallClicked(View view) {
        if (isCalling) {
            isCalling = false;
            removeRemoteView();
            removeLocalView();
            leaveChannel();
            emptyChat();
            isLive = false;
            checkIfAbleToSendMessage();
        }else {
            isCalling = true;
            setUpLocalView();
            joinChannel();
            isLive = true;
            checkIfAbleToSendMessage();
        }
    }

    private void emptyChat() {
        mMessageBeanList.clear();
        mRecyclerView.scrollToPosition(mMessageBeanList.size() - 1);
    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
    }

    private void removeLocalView() {
        if (mLocalView != null) {
            mLocalContainer.removeView(mLocalView);
        }
        mLocalView = null;
    }

    private void removeRemoteView() {
        if (mRemoteView != null) {
            mRemoteContainer.removeView(mRemoteView);
        }
        mRemoteView = null;
    }

    public void onSwitchCameraClicked(View view) {
        mRtcEngine.switchCamera();
    }

    public void onLocalAudioMuteClicked(View view) {
        isMuted = !isMuted;
        mRtcEngine.muteLocalAudioStream(isMuted);
        mMuteBtn.setImageResource(isMuted ? R.drawable.btn_mute : R.drawable.btn_unmute);
    }

    public void onSendClicked(View view) {

        if (!isAbleToSendMessage) {
            return;
        }

        MessageBean messageBean = new MessageBean(pnConfiguration.getUuid(), editText.getText().toString(), true);
        mMessageBeanList.add(messageBean);
        mMessageAdapter.notifyItemRangeChanged(mMessageBeanList.size(), 1);
        mRecyclerView.scrollToPosition(mMessageBeanList.size() - 1);

        // create message payload using Gson
        final JsonObject messageJsonObject = new JsonObject();
        messageJsonObject.addProperty("msg", editText.getText().toString());

        pubnub.publish().channel(channelName).message(messageJsonObject).async(new PNCallback<PNPublishResult>() {
            @Override
            public void onResponse(PNPublishResult result, PNStatus status) {
                // Check whether request successfully completed or not.
                if (!status.isError()) {
                    editText.setText("");
                    // Message successfully published to specified channel.
                }
                // Request processing failed.
                else {

                    // Handle message publish error. Check 'category' property to find out possible issue
                    // because of which request did fail.
                    //
                    // Request can be resent using: [status retry];
                }
            }
        });
    }

    private void checkIfAbleToSendMessage() {
        if (isLive && isRemoteLive) {
            isAbleToSendMessage = true;
            mChatLayout.setVisibility(View.VISIBLE);
            mMessageSendLayout.setVisibility(View.VISIBLE);
        }else {
            isAbleToSendMessage = false;
            mChatLayout.setVisibility(View.INVISIBLE);
            mMessageSendLayout.setVisibility(View.INVISIBLE);
        }
    }
}