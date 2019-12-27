package cn.com.chioy.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.com.chioy.myapplication.bluetoothuitl.ClientThread;
import cn.com.chioy.myapplication.bluetoothuitl.ConnectedSocket;
import cn.com.chioy.myapplication.bluetoothuitl.Params;
import cn.com.chioy.myapplication.bluetoothuitl.ServerThread;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class DeviceListFragment extends Fragment {

    final String TAG = "DeviceListFragment";

    ListView listView;
    MyListAdapter listAdapter;
    List<BluetoothDevice> deviceList = new ArrayList<>();

    BluetoothAdapter bluetoothAdapter;
    MyBtReceiver btReceiver;
    IntentFilter intentFilter;

    MainActivity mainActivity;
    Handler uiHandler;

    ClientThread clientThread;
    ServerThread serverThread;
    ConnectedSocket connectedSocket;

    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        switch (requestCode) {
            case Params.MY_PERMISSION_REQUEST_CONSTANT: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 运行时权限已授权
                }
                return;
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "--------------- 不支持蓝牙");
            getActivity().finish();
        }
        setDiscoverableTimeout(100000);
        intentFilter = new IntentFilter();
        btReceiver = new MyBtReceiver();
        intentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        getActivity().registerReceiver(btReceiver, intentFilter);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bt_list, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        listView = (ListView) view.findViewById(R.id.device_list_view);
        listAdapter = new MyListAdapter();
        listView.setAdapter(listAdapter);
        listAdapter.notifyDataSetChanged();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 选择了作为客户端主动建立通道，关闭服务器监听
                closeListen();
                BluetoothDevice device = deviceList.get(position);

                // 开启客户端线程，连接点击的远程设备
                clientThread = new ClientThread(bluetoothAdapter, device, uiHandler, DeviceListFragment.this);
                new Thread(clientThread).start();

                // 通知 ui 连接的服务器端设备
                Message message = new Message();
                message.what = Params.MSG_CONNECT_TO_SERVER;
                message.obj = device;
                uiHandler.sendMessage(message);

            }
        });

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mainActivity = (MainActivity) getActivity();

        uiHandler = mainActivity.getUiHandler();
        serverThread = new ServerThread(bluetoothAdapter, uiHandler, DeviceListFragment.this);
        new Thread(serverThread).start();
    }

    @Override
    public void onResume() {
        super.onResume();

        // 蓝牙未打开，询问打开
        if (!bluetoothAdapter.isEnabled()) {
            Intent turnOnBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOnBtIntent, Params.REQUEST_ENABLE_BT);
            return;
        }

        // 蓝牙已开启
        if (bluetoothAdapter.isEnabled()) {
            showBondDevice();
        }
    }

    public void setDiscoverableTimeout(int timeout) {
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        try {
            Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
            setDiscoverableTimeout.setAccessible(true);
            Method setScanMode =BluetoothAdapter.class.getMethod("setScanMode", int.class,int.class);
            setScanMode.setAccessible(true);

            setDiscoverableTimeout.invoke(adapter, timeout);
            setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,timeout);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeDiscoverableTimeout() {
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        try {
            Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
            setDiscoverableTimeout.setAccessible(true);
            Method setScanMode =BluetoothAdapter.class.getMethod("setScanMode", int.class,int.class);
            setScanMode.setAccessible(true);

            setDiscoverableTimeout.invoke(adapter, 1);
            setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE,1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeDiscoverableTimeout();
        getActivity().unregisterReceiver(btReceiver);
        realeaseSocket();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.enable_visibility:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                enableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 600);
                startActivityForResult(enableIntent, Params.REQUEST_ENABLE_VISIBILITY);
                break;
            case R.id.discovery:
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                if (Build.VERSION.SDK_INT >= 6.0) {
                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            Params.MY_PERMISSION_REQUEST_CONSTANT);
                }
                bluetoothAdapter.startDiscovery();
                break;
            case R.id.disconnect:
                bluetoothAdapter.disable();
                deviceList.clear();
                listAdapter.notifyDataSetChanged();
                toast("蓝牙已关闭");
                break;
        }
        return super.onOptionsItemSelected(item);

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Params.REQUEST_ENABLE_BT: {
                if (resultCode == RESULT_OK) {
                    showBondDevice();
                }
                break;
            }
            case Params.REQUEST_ENABLE_VISIBILITY: {
                if (resultCode == 600) {
                    toast("蓝牙已设置可见");
                } else if (resultCode == RESULT_CANCELED) {
                    toast("蓝牙设置可见失败,请重试");
                }
                break;
            }
        }
    }

    /**
     * 用户打开蓝牙后，显示已绑定的设备列表
     */
    private void showBondDevice() {
        deviceList.clear();
        Set<BluetoothDevice> tmp = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d :tmp) {
            Log.d(TAG, "showBondDevice: name--"+d.getName()+"---type---"+d.getType());
            deviceList.add(d);
        }
        listAdapter.notifyDataSetChanged();
    }

    /**
     * Toast 提示
     */
    public void toast(String str) {
        Toast.makeText(getContext(), str, Toast.LENGTH_SHORT).show();
    }

    //客户端或服务端建立了连接
    public void setConnectedSocket(ConnectedSocket connectedSocket) {
        //已经得到Socket，关闭服务端和客户端线程
        closeListen();
        this.connectedSocket = connectedSocket;
    }


    /**
     * 资源释放
     */
    public void realeaseSocket() {
        if (connectedSocket != null) {
            connectedSocket.cancel();
        }
        closeListen();
    }

    /***连接监听释放*/
    private void closeListen() {
        if (serverThread != null) {
            serverThread.cancel();
            serverThread = null;
        }
    }

    /**
     * 向 socket 写入发送的数据
     *
     * @param dataSend
     */
    public void writeData(String dataSend) {
//        Message message =new Message();
//        message.obj = dataSend;
//        if (serverThread!=null){
//            message.what=Params.MSG_SERVER_WRITE_NEW;
//            serverThread.writeHandler.sendMessage(message);
//        }
//        if (clientThread!=null){
//            message.what=Params.MSG_CLIENT_WRITE_NEW;
//            clientThread.writeHandler.sendMessage(message);
//        }
//        if (serverThread != null) {
//            serverThread.write(dataSend);
//        } else if (clientThread != null) {
//            clientThread.write(dataSend);
//        }

        if (connectedSocket != null) {
            connectedSocket.write(dataSend);
        } else {
            toast("没有建立任何通信");
        }
    }

    public void openService() {
        serverThread = new ServerThread(bluetoothAdapter, uiHandler, DeviceListFragment.this);
        new Thread(serverThread).start();
    }


    /**
     * 设备列表的adapter
     */
    private class MyListAdapter extends BaseAdapter {

        public MyListAdapter() {
        }

        @Override
        public int getCount() {
            return deviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return deviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R.layout.layout_item_bt_device, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) convertView.findViewById(R.id.device_name);
                viewHolder.deviceMac = (TextView) convertView.findViewById(R.id.device_mac);
                viewHolder.deviceState = (TextView) convertView.findViewById(R.id.device_state);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            int code = deviceList.get(position).getBondState();
            String name = deviceList.get(position).getName();
            String mac = deviceList.get(position).getAddress();
            String state;
            if (name == null || name.length() == 0) {
                name = "未命名设备";
            }
            if (code == BluetoothDevice.BOND_BONDED) {
                state = "ready";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.green));
            } else {
                state = "new";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.red));
            }
            if (mac == null || mac.length() == 0) {
                mac = "未知 mac 地址";
            }
            viewHolder.deviceName.setText(name);
            viewHolder.deviceMac.setText(mac);
            viewHolder.deviceState.setText(state);
            return convertView;
        }

    }

    /**
     * 与 adapter 配合的 viewholder
     */
    static class ViewHolder {
        public TextView deviceName;
        public TextView deviceMac;
        public TextView deviceState;
    }

    /**
     * 广播接受器
     */
//    private class MyBtReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            BluetoothDevice device;
//            switch (intent.getAction()) {
////              case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
////              case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
//                case BluetoothDevice.ACTION_FOUND:
//                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                    if (isNewDevice(device)) {
//                        deviceList.add(device);
//                        listAdapter.notifyDataSetChanged();
//                        Log.e(TAG, "---------------- " + device.getName());
//                    }
//
//                    break;
//                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
//                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
//                   // device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                    switch (bondState) {
//                        case BluetoothDevice.BOND_BONDED:  //配对成功
//                            //取消搜索，连接蓝牙设备
//                            break;
//                        case BluetoothDevice.BOND_BONDING:
//                            break;
//                        case BluetoothDevice.BOND_NONE:
//                            break;
//                        default:
//                            break;
//                    }
//                    break;
//                case BluetoothAdapter.ACTION_STATE_CHANGED:
//                   int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
//                    switch (state) {
//                        case BluetoothAdapter.STATE_TURNING_ON:
//                            Log.i(TAG,"BluetoothAdapter is turning on.");
//                            break;
//                        case BluetoothAdapter.STATE_ON:
//                            Log.i(TAG,"BluetoothAdapter is on.");
//                           //蓝牙已打开，开始搜索并连接service
//                            break;
//                        case BluetoothAdapter.STATE_TURNING_OFF:
//                            Log.i(TAG,"BluetoothAdapter is turning off.");
//                            break;
//                        case BluetoothAdapter.STATE_OFF:
//                            Log.i(TAG,"BluetoothAdapter is off.");
//                            break;
//                    }
//                    break;
//                default:
//                    break;
//            }
//        }
//    }

    /**
     * 广播接受器
     */
    private class MyBtReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("android.bluetooth.device.action.PAIRING_REQUEST")){
                //弹出绑定弹窗，以下处理弹窗
                BluetoothDevice mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                try {
                    //确认绑定
                    Method setPairingConfirmation = mBluetoothDevice.getClass().getDeclaredMethod("setPairingConfirmation", boolean.class);
                    setPairingConfirmation.invoke(mBluetoothDevice, true);
                    //终止弹窗广播
                    abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
                    //调用setPin方法进行配对...
                   setPin(mBluetoothDevice.getClass(), mBluetoothDevice, "你需要设置的PIN码");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
               BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (bondState){
                    case BluetoothDevice.BOND_BONDED:  //配对成功
                        Log.d(TAG, "Device:"+device.getName()+" bonded.");
                        //取消搜索，连接蓝牙设备
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "Device:"+device.getName()+" bonding.");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "Device:"+device.getName()+" not bonded.");
                        break;
                    default:
                        break;
                }
                return;
            }



            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                toast("开始搜索 ...");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                toast("搜索结束");
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (isNewDevice(device)) {
                    deviceList.add(device);
                    listAdapter.notifyDataSetChanged();
                    Log.e(TAG, "---------------- " + device.getName());
                }
            }
        }
    }


     private boolean setPin(Class<? extends BluetoothDevice> btClass, BluetoothDevice btDevice,String str) {
        try {
            Method removeBondMethod = btClass.getDeclaredMethod("setPin",
                    new Class[]
                            {byte[].class});
            Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice,
                    new Object[]
                            {str.getBytes()});
            Log.e("returnValue", "" + returnValue);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 判断搜索的设备是新蓝牙设备，且不重复
     *
     * @param device
     * @return
     */
    private boolean isNewDevice(BluetoothDevice device) {
        boolean repeatFlag = false;
        for (BluetoothDevice d :
                deviceList) {
            if (d.getAddress().equals(device.getAddress())) {
                repeatFlag = true;
            }
        }
        //不是已绑定状态，且列表中不重复
        return device.getBondState() != BluetoothDevice.BOND_BONDED && !repeatFlag;
    }
}
