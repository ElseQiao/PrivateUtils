package cn.com.chioy.myapplication.mywork;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Created by Else.
 * Date: 2019/12/27
 * Time: 15:50
 * Describe:经典蓝牙实例，对超时后重新连接、跳过以及流程进行了优化
 */
public class ClassBluetooth {
    private static final String TAG = "ClassBluetooth";
    private Context context;
    private String bluetoothName;
    private MyBtReceiver btReceiver;
    private boolean isDeviceFind = false;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectThread mConnectThread;//连接线程
    private ConnectedThread mConnectedThread;//通信线程
    private Handler uiHandler;

    public ClassBluetooth(Context context, Handler uiHandler) {
        this.context = context;
        this.uiHandler = uiHandler;
    }

    public void initBluetoothDevice(String deviceName) {
        bluetoothName = deviceName;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initReceiver();
        if (bluetoothAdapter == null) {
            // LogUtil.d("TAG", "没有蓝牙模块");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
//        if (Build.VERSION.SDK_INT >= 6.0) {
//            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    Params.MY_PERMISSION_REQUEST_CONSTANT);
//        }
        isDeviceFind = false;
        startDeviceDiscovery(10);
        startScanNotify();
    }

    //传统蓝牙扫描是个重量操作，只会扫描12s，之后扫描页面直至停止，发送停止广播
    //由于测试设备无法每次都获取停止广播，所以通过handler实现反复扫描
    private void startDeviceDiscovery(long delay) {
        if (!isDeviceFind) {
            uiHandler.postDelayed(discoveryRunnable, delay);
        } else {
            stopDeviceDiscovery();
        }
    }

    private Runnable discoveryRunnable = new Runnable() {
        @Override
        public void run() {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            bluetoothAdapter.startDiscovery();
            startDeviceDiscovery(10000);
        }
    };

    private void stopDeviceDiscovery() {
        uiHandler.removeCallbacks(discoveryRunnable);
        bluetoothAdapter.cancelDiscovery();
    }

    private void initReceiver() {
        btReceiver = new MyBtReceiver();
        IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(btReceiver, intentFilter);
    }


    public void release() {
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        stopDeviceDiscovery();
        context.unregisterReceiver(btReceiver);
    }


    /**
     * 广播接受器
     */
    private class MyBtReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "开始搜索 ...");
                return;
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "一次搜索结束 ...");
                //if(!isDeviceFind){
                //未扫描到蓝牙，继续扫描
                //   bluetoothAdapter.startDiscovery();
                //}
                return;
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.e(TAG, "---------------- " + device.getName());
                checkDevice(device);
                return;
            }

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (bondState) {
                    case BluetoothDevice.BOND_BONDED:  //配对成功
                        Log.d(TAG, "Device:" + device.getName() + " bonded.");
                        checkDevice(device);
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "Device:" + device.getName() + " bonding.");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "Device:" + device.getName() + " not bonded.");
                        break;
                    default:
                        break;
                }
                return;
            }

//            if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
//                //弹出绑定弹窗，以下处理弹窗
//                BluetoothDevice mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                try {
//                    //确认绑定
//                    Method setPairingConfirmation = mBluetoothDevice.getClass().getDeclaredMethod("setPairingConfirmation", boolean.class);
//                    setPairingConfirmation.invoke(mBluetoothDevice, true);
//                    //终止弹窗广播
//                    abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
//                    //调用setPin方法进行配对...
//                    setPin(mBluetoothDevice.getClass(), mBluetoothDevice, "你需要设置的PIN码");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                return;
//            }
        }
    }


    private void checkDevice(BluetoothDevice device) {
        String name = device.getName();
        if (name == null) {

        } else if (name.equals(bluetoothName)) {
            //LogUtil.d("TAG", "找到蓝牙,停止搜索");
            Log.d(TAG, "找到蓝牙,停止搜索 ...");
            isDeviceFind = true;
            stopDeviceDiscovery();
            startConnect();
            connectDevice(device);
        } else {
            //LogUtil.d("TAG", "蓝牙:" + name);
            Log.d(TAG, "蓝牙:" + name);
        }
    }

    /**
     * 用来连接一个已经绑定的设备，如果设备未绑定，则先绑定设备
     */
    private void connectDevice(BluetoothDevice device) {
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            connect(device);
        } else {
            try {
                //连接建立之前的先配对
                Method creMethod = BluetoothDevice.class.getMethod("createBond");
                creMethod.invoke(device);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "蓝牙配对异常");
            }
        }

    }


    //设置默认pin码，跳过配对
    private boolean setPin(Class<? extends BluetoothDevice> btClass, BluetoothDevice btDevice, String str) {
        try {
            Method removeBondMethod = btClass.getDeclaredMethod("setPin",
                    new Class[]{byte[].class});
            Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice,
                    new Object[]{str.getBytes()});
            Log.d("returnValue", "" + returnValue);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    //由于传统蓝牙的扫描是个重量级的过程，所以对反复扫描做些特殊处理
    private void startScanNotify() {
        //TODO 开始扫描，通知UI
        Message message = new Message();
        message.what = 1;
        message.obj = "开始扫描";
        uiHandler.sendMessage(message);

        //反复扫描处理

    }

    private void startConnect() {
        //TODO 开始连接，通知UI
        Message message = new Message();
        message.what = 1;
        message.obj = "开始连接";
        uiHandler.sendMessage(message);
    }

    private void connectionFailed() {
        //TODO 连接设备失败，通知UI
        mState = STATE_NONE;
        Message message = new Message();
        message.what = 1;
        message.obj = "连接设备失败";
        uiHandler.sendMessage(message);
    }

    private void connectSuccess() {
        //TODO 连接成功可以通信，通知UI
        mState = STATE_CONNECTED;
        Message message = new Message();
        message.what = 1;
        message.obj = "连接成功可以通信";
        uiHandler.sendMessage(message);
    }

    private void connectionLost() {
        //TODO 失去连接，通知UI
        mState = STATE_NONE;
        Message message = new Message();
        message.what = 1;
        message.obj = "失去连接";
        uiHandler.sendMessage(message);
    }


    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }


    public static final String MN_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    private int mState = 0;

    /**
     * 此线程在尝试与设备进行传出连接时运行。可以从这里知道连接成功或失败。
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                //tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(MN_UUID));
                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(MN_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread .....");
            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();
            try {
                //阻塞，返回成功或者返回异常
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // 完成连接，可以设置为空
            synchronized (ClassBluetooth.this) {
                mConnectThread = null;
            }

            // 开始通信
            manageConnectedSocket(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public synchronized void manageConnectedSocket(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "manageConnectedSocket");
        // 取消连接线程
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // 取消当前通信线程
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        //开启新的通信线程
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    /**
     * 该线程用于连接后通信使用
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ...");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            connectSuccess();
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread...");
            byte[] buffer = new byte[1024];
            int len;
            List<Byte> totalByte=null;
            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    //为了减少资源消耗，此处是阻塞式InputStream，无数据式阻塞在此处。
                    len = mmInStream.read(buffer);

                    if(buffer[0]==11){
                        if(totalByte!=null){
                            byte[] miss= listToArray(totalByte);
                            Log.e(TAG, "丢失不完整数据：miss data----" + new String(miss,"GBK"));
                        }
                        //0x0B <SB> 对应byte值11 。表示一条数据的开始,创建一个list缓存区，将本次数据保存进去
                        totalByte=new ArrayList<>();
                        for(int i=0;i<len;i++){
                                totalByte.add(buffer[i]);
                        }
                    }else{
                        //将信息拼接
                        for(int i=0;i<len;i++){
                            totalByte.add(buffer[i]);
                        }
                    }

                    if(totalByte!=null&&totalByte.size()>2&&totalByte.get(totalByte.size()-2)==28){
                        //定义结尾是<EB><CR> 用16进制0x1c,0x1d 对应byte值 28，13，所以检测到倒数第二个值为28，说明本次
                        //数据传完
                        receiveMsg(totalByte);
                        totalByte.clear();
                        totalByte=null;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "-------------连接断开 isConnected：false");
                    connectionLost();
                    break;
                }
            }
        }

        private byte[] listToArray(List<Byte> list) {
            if (list == null || list.size() < 0)
                return null;
            byte[] bytes = new byte[list.size()];
            int i = 0;
            Iterator<Byte> iterator = list.iterator();
            while (iterator.hasNext()) {
                bytes[i] = iterator.next();
                i++;
            }
            return bytes;
        }

        /**
         * 接收到的检查结果
         * 注意：生化设备这里不仅可以接收到实时检查的结果，还可以手动上传生化设备内保存历史检查结果，目前，客
         * 户端无法区分是哪种情况下传过来的数据。所以如果需要保存至服务器的需要校验数据是否重复
         *
         * 客户端方案：
         * 1.实时检查每次都需要十分钟以上，所以如果短时间收到多条数据，基本可以判定是手动上传。
         *   缺陷：如果手动上传一条数据或者实时检测数据也混在其中的情况下无法判断
         * */
        private void receiveMsg(List<Byte> totalByte)   {
            if(totalByte.indexOf((byte)11)==-1){
                //数据不正常
                return;
            }
            if(totalByte.indexOf((byte)11)==totalByte.lastIndexOf((byte)11)){
                //说明只有一条数据,实际检查只有一条数据（或者点击上传时）
                //解析时跳过开头0x0B(<SB>)从第二个byte开始转码，去除结尾3个16进制（0x<CR><EB><CR>），一共需要转码totalByte.size()-3
                String result;
                try {
                     result=new String(listToArray(totalByte),1,totalByte.size()-4,"GBK");
                } catch (UnsupportedEncodingException e) {
                    result=new String(listToArray(totalByte),1,totalByte.size()-4);
                    e.printStackTrace();
                }
                Log.d(TAG, "receiveMsg 只有一条数据"+result);
                Message message = new Message();
                message.what = 2;
                message.obj = result;
                uiHandler.sendMessage(message);

            }else{
                //说明有多条数据，目前测试发现设备上传
                Log.d(TAG, "receiveMsg 多条数据综合: "+new String(listToArray(totalByte)));
            }
        }





        /**
         * 写入
         *  read(byte[]) 和 write(byte[]) 方法都是阻塞调用。read(byte[]) 将会阻塞，直至从流式传输中读取内
         * 容。write(byte[]) 通常不会阻塞，但如果远程设备没有足够快地调用 read(byte[])，并且中间缓冲区已满，
         * 则其可能会保持阻塞状态以实现流量控制。因此，线程中的主循环应专门用于读取 InputStream。
         * 可使用线程中单独的公共方法来发起对 OutputStream 的写入操作
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void write(String data) {
            //data = data+"\r\n";
            try {
                mmOutStream.write(data.getBytes("utf-8"));
                // testData.write(data.getBytes());
                Log.e(TAG, "---------- write data ok " + data);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


}

