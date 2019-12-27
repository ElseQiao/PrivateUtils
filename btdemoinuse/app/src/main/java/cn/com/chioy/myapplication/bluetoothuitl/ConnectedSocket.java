package cn.com.chioy.myapplication.bluetoothuitl;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectedSocket implements Runnable {
    private static final String TAG = "ConnectedSocket";
    OutputStream out;
    InputStream in;
    boolean isConnected = true;
    BluetoothSocket socket;
    private Handler uiHandler;

    //private DataOutputStream testData;

    public ConnectedSocket(BluetoothSocket socket, Handler uiHandler) {
        this.socket = socket;
        this.uiHandler = uiHandler;
        try {
            out = socket.getOutputStream();
            in = socket.getInputStream();
           // testData=new DataOutputStream(out);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    @Override
    public void run() {
        // 已连接
        Log.e(TAG, "-------------连接建立 isConnected：true");
        byte[] buffer = new byte[1024];
        int len;
        String content;
        String total="";
        while (isConnected) {
                try {
                    //为了减少资源消耗，此处是阻塞式InputStream，无数据式阻塞在此处。
                   len = in.read(buffer);
                   content = new String(buffer, 0, len);
                   //socket获取时，read(buffer)不能保证每次都是1024个字节，只能保证最大为1024
                    //所以很难保证一次性读取所有数据，因此采用下列方式进行拼接，只有合成一条完整数据后才发给
                    //客户端更新
                   if(content.startsWith("<SB>")){
                       //一条新数据
                       if(!"".equals(total)){
                           Log.e(TAG, "miss data----"+total);
                       }
                       total=content;
                   }else{
                       total=total+content;
                       Log.d(TAG, "part data----"+content);
                   }

                   if(dataCheck(total)){
                       Message message = new Message();
                       message.what = Params.MSG_SERVER_REV_NEW;
                       message.obj = total;
                       uiHandler.sendMessage(message);
                       Log.d(TAG, "total data----收到消息内容："+total);
                       total="";
                   }
                } catch (IOException e) {
                    e.printStackTrace();
                    //TODO 状态提醒
                    Log.e(TAG, "-------------连接断开 isConnected：false");
                    lostConnection();
                    break;
                }
        }
    }

    private boolean dataCheck(String content) {
        if(content.startsWith("<SB>")&&content.endsWith("<EB><CR>")){
            //说明是一条完整数据
            return true;
        }
        return false;
    }

    public void write(String data){
        //data = data+"\r\n";
        try {
            out.write(data.getBytes("utf-8"));
           // testData.write(data.getBytes());
            Log.e(TAG, "---------- write data ok "+data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void lostConnection() {
        uiHandler.sendEmptyMessage(404);
        isConnected=false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cancel() {
        //TODO 所有断开连接，在这里处理（已处理：socket断开；未处理：蓝牙关闭）
        isConnected=false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
