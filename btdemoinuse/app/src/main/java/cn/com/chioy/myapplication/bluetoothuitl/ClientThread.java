package cn.com.chioy.myapplication.bluetoothuitl;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import cn.com.chioy.myapplication.DeviceListFragment;


public class ClientThread implements Runnable {

    final String TAG = "ClientThread";

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice device;

    Handler uiHandler;
    BluetoothSocket socket;
    DeviceListFragment deviceListFragment;


    public ClientThread(BluetoothAdapter bluetoothAdapter, BluetoothDevice device,
                        Handler handler,DeviceListFragment deviceListFragment) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.device = device;
        this.deviceListFragment=deviceListFragment;
        this.uiHandler = handler;
        BluetoothSocket tmp = null;
        try {
            tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(Params.UUID));
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = tmp;
    }


    @Override
    public void run() {

        Log.e(TAG, "----------------- do client thread run()");
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }


        Log.e(TAG, "-----------------开始主动建立连接");
        try {
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            // Unable to connect; close the socket and get out
            //TODO 请确认对方设备是否可连接
            Log.e(TAG, "-----------------连接Exception");
            try {
                socket.close();
            } catch (IOException closeException) { }
            return;
        }
        Log.e(TAG, "-----------------连接成功");

        manageConnectedSocket(socket);

    }

    private ConnectedSocket connectedSocket;
    private void manageConnectedSocket(BluetoothSocket socket) {
       // message.what = Params.MSG_CLIENT_REV_NEW;
        connectedSocket = new ConnectedSocket(socket, uiHandler);
        new Thread(connectedSocket).start();
        deviceListFragment.setConnectedSocket(connectedSocket);
    }

}
