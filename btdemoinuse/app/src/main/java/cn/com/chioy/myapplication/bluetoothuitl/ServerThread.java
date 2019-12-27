package cn.com.chioy.myapplication.bluetoothuitl;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import cn.com.chioy.myapplication.DeviceListFragment;


public class ServerThread implements Runnable {

    final String TAG = "ServerThread";
    BluetoothAdapter bluetoothAdapter;
    BluetoothServerSocket serverSocket =null;
    Handler uiHandler;
    DeviceListFragment deviceListFragment;


    boolean acceptFlag = true;

    public ServerThread(BluetoothAdapter bluetoothAdapter, Handler handler, DeviceListFragment deviceListFragment) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.uiHandler = handler;
        this.deviceListFragment=deviceListFragment;
        BluetoothServerSocket tmp = null;
        try {
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(Params.NAME, UUID.fromString(Params.UUID));
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverSocket = tmp;
    }


    @Override
    public void run() {
             Log.e(TAG, "-----------------蓝牙服务器开启");
            while (acceptFlag) {
                BluetoothSocket socket = null;
                // Keep listening until exception occurs or a socket is returned
                if(serverSocket!=null){
                    try {
                        socket = serverSocket.accept();
                    } catch (IOException e) {
                        break;
                    }
                    // If a connection was accepted
                    if (socket != null) {
                        Log.e(TAG, "-----------------收到建立连接的请求");
                        // Do work to manage the connection (in a separate thread)
                        manageConnectedSocket(socket);
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
    }

    private ConnectedSocket connectedSocket;
    private void manageConnectedSocket(BluetoothSocket socket) {
        BluetoothDevice remoteDevice = socket.getRemoteDevice();
        Message message = new Message();
        message.what = Params.MSG_REV_A_CLIENT;
        message.obj = remoteDevice;
        uiHandler.sendMessage(message);
        connectedSocket = new ConnectedSocket(socket, uiHandler);
        new Thread(connectedSocket).start();
        deviceListFragment.setConnectedSocket(connectedSocket);
    }


    public void cancel() {
        try {
            acceptFlag = false;
            if(serverSocket!=null) {
                serverSocket.close();
            }
            Log.e(TAG, "-------------- do cancel ,flag is "+acceptFlag);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "----------------- cancel " + TAG + " error");
        }
    }
}
