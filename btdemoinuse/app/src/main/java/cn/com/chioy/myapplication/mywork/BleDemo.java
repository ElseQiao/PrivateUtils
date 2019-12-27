package cn.com.chioy.myapplication.mywork;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

/**
 * Created by Else.
 * Date: 2019/12/27
 * Time: 15:59
 * Describe:ble蓝牙实例
 */
public class BleDemo {
    private static final String TAG = "BleDemo";
    private Context mContext;
    private BluetoothLeScanner scanner;
    private String deviceName;
    private static final UUID ACS_SERVICE_UUID = UUID.fromString("0000FFB0-0000-1000-8000-00805f9b34fb");
    private static final UUID DATA_LINE_UUID = UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb");
    private static final UUID MAIN_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static Float tems = 0.0f;
    private BluetoothGatt bluetoothGatt;

    public BleDemo(Context context) {
        mContext = context;
    }


    public void connectBluetoothDevice(String deviceNme) {
        this.deviceName = deviceNme;
        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.d(TAG, "connectBluetoothDevice: 蓝牙打开,请打开蓝牙重启软件!");
        } else {
            Log.d(TAG, "connectBluetoothDevice: 搜索体温计蓝牙");
            scanner.startScan(scanCallback);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            String name = result.getDevice().getName();
            if (name == null) {
                return;
            }
          //  LogUtil.d(name);
            if (deviceName.equals(name)) {
                scanner.stopScan(this);
                Log.d(TAG, "scanCallback: 发现体温计蓝牙");
                BluetoothDevice device = result.getDevice();
                connect(device);
            }

        }
    };

    private void connect(BluetoothDevice device) {
        Log.d(TAG, "connect: 连接体温计蓝牙");
        device.connectGatt(mContext, false, callback);
    }

    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            bluetoothGatt = gatt;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                Log.d(TAG, "connect: 连接体温蓝牙成功");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "connect: 体温蓝牙断开");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    Log.d(TAG, "connect: 搜索体温计蓝牙服务");
                    BluetoothGattService service = gatt.getService(ACS_SERVICE_UUID);
                    if (service == null) {
                        return;
                    }
                    BluetoothGattCharacteristic chara = service.getCharacteristic(DATA_LINE_UUID);
                    if (chara == null) {
                        return;
                    }
                    gatt.setCharacteristicNotification(chara, true);
                    BluetoothGattDescriptor descriptor = chara.getDescriptor(MAIN_UUID);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                    break;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final byte[] data = characteristic.getValue();
            Log.d(TAG, "onCharacteristicChanged: 收到传输数据");
        }
    };

    public void stopBluetoothDevice() {
        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            return;
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        if (scanner != null && scanCallback != null) {
            scanner.stopScan(scanCallback);
        }
    }
}
