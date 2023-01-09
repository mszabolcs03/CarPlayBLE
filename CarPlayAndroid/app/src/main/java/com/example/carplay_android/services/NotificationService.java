package com.example.carplay_android.services;

import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.carplay_android.utils.BroadcastUtils;
import com.example.carplay_android.utils.DirectionUtils;


public class NotificationService extends NotificationListenerService {

    private BleService.BleBinder controlBle;
    private MyServiceConn serviceConnToBle;
    private Boolean deviceStatus = false;


    public NotificationService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        init();
        BroadcastUtils.sendStatus(true, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        DirectionUtils.loadSamplesFromAsserts(getApplicationContext());
    }



    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn != null && isGMapNotification(sbn)){
            handleGMapNotification(sbn);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        Log.d("Notification","removed");
    }



    private boolean isGMapNotification(StatusBarNotification sbn){
        if(!sbn.isOngoing() || !sbn.getPackageName().contains("com.google.android.apps.maps")){
            return false;
        }
        return (sbn.getId() == 1);
    }


    private void handleGMapNotification (StatusBarNotification sbn){
        Bundle bundle = sbn.getNotification().extras;
        String informationMessage;
        String string = bundle.getString(Notification.EXTRA_TEXT);
        String[] strings = string.split("-");//destination
        informationMessage = strings[0].trim() + "$";
        strings = strings[1].trim().split(" ");
        if(strings.length == 3){
            strings[0] = strings[0] + " ";//concat a " "
            strings[0] = strings[0] + strings[1];//if use 12 hour type, then concat the time and AM/PM
        }
        informationMessage = informationMessage + strings[0] + "$";// get the ETA

        string = bundle.getString(Notification.EXTRA_TITLE);
        strings = string.split("-");
        if(strings.length  == 2){
            informationMessage = informationMessage + strings[0].trim() + "$"  + strings[1].trim() + "$";//time to next direction + Direction to somewhere
        }
        else if(strings.length  == 1){
            informationMessage = informationMessage + strings[0] + "$";//Direction to somewhere
            bundle.putString("Direction",strings[0]);
        }

        string = bundle.getString(Notification. EXTRA_SUB_TEXT);
        strings = string.split("·");
        informationMessage = informationMessage + strings[0].trim() + "$" + strings[1].trim() + "$";// ETA in Minutes + Distance

        BitmapDrawable bitmapDrawable = (BitmapDrawable) sbn.getNotification().getLargeIcon().loadDrawable(getApplicationContext());

        informationMessage = informationMessage + DirectionUtils.getDirectionByComparing(bitmapDrawable.getBitmap());
        if(deviceStatus){
            controlBle.sendToDevice(informationMessage);
        }
    }

    private void init(){
        initService();
        initBroadcastReceiver();
    }

    private void initService(){
        serviceConnToBle = new MyServiceConn();
        Intent intent = new Intent(this, BleService.class);
        bindService(intent, serviceConnToBle, BIND_AUTO_CREATE);
        startService(intent);//bind the service
    }

    private void initBroadcastReceiver(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        ReceiverForDeviceStatus receiverForDeviceStatus = new ReceiverForDeviceStatus();
        IntentFilter intentFilterForDeviceStatus = new IntentFilter(getFILTER_DEVICE_STATUS());
        localBroadcastManager.registerReceiver(receiverForDeviceStatus, intentFilterForDeviceStatus);
    }

    private class MyServiceConn implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder){
            controlBle = (BleService.BleBinder)iBinder;
        }
        @Override
        public void onServiceDisconnected(ComponentName name){
        }
    }

    private class ReceiverForDeviceStatus extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            deviceStatus = intent.getBooleanExtra(getFILTER_DEVICE_STATUS(), false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BroadcastUtils.sendStatus(false, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        unbindService(serviceConnToBle);
    }
}