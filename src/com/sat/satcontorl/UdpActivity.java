package com.sat.satcontorl;

import android.R.string;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.sat.satcontorl.utils.IpInfo;
import com.sat.satcontorl.utils.ScanDevices;

public class UdpActivity extends Activity implements OnClickListener {

	private final static String TAG = "H264";

	private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video

	// hdb
	private final static int VIDEO_WIDTH = 961;
	private final static int VIDEO_HEIGHT = 600;

//	private final static int FRAME_RATE = 25;
	private final static int FRAME_INTERVAL = 1;
//	private final static int FRAME_BIT_RATE = 614400;
	
	private final static int FRAME_RATE = 10;
	private final static int FRAME_BIT_RATE = 2000000;

	protected static final int CONNET_SUCCESS = 1;

	protected static final int SCAN_IP_OVER = 2;
	
	protected static final int CLEAR_FAILCOUNT = 3;

	protected static final int CONNECT_FAIL = 5;

	private static final String IP_ADDRESS = "ipaddress";

	// 用于屏幕数据
//	private Socket dataSocket;
	// 用于触摸
//	private Socket touchSocket;

//	private DataOutputStream dos;

	private float densityX = 0;
	private float densityY = 0;

	private int changeX = 0;
	private int changeY = 0;

	private SurfaceView mSurfaceView;
	private MediaCodec mCodec;

	private List<String> mIps;
	private ArrayList<IpInfo> ipInfos = new ArrayList<IpInfo>();
	private MyAdapter mAdapter;

	private Handler mHandler = new Handler() {

		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CONNET_SUCCESS:
				if (llInfo != null) {
					llInfo.setVisibility(View.GONE);
				}
				
				break;

			case SCAN_IP_OVER:
				if (mPBar != null && mDialog != null) {
					mPBar.setVisibility(View.GONE);
				}
				
				if (mIps != null && mIps.size() > 0 && mDialog != null && lvIp != null) {
					if (mAdapter == null) {
						setListAdapter();
					}else {
						mAdapter.notifyDataSetChanged();
					}
				}
				break;
				
			case CONNECT_FAIL:
				onBackPressed();
				break;
				
			case CLEAR_FAILCOUNT:
				failCount = 0;
				break;



			default:
				break;
			}
		};
	};

	public class ViewHolder {
		TextView mIpItem;
	}
	private void setListAdapter(){
		mAdapter = new MyAdapter();
		lvIp.setAdapter(mAdapter);
		lvIp.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				etIp.setText(ipInfos.get(position).getIpAddress());
				sp.edit().putString(IP_ADDRESS, ipInfos.get(position).getIpAddress()).commit();
				if (mDialog != null) {
					mDialog.dismiss();
				}
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initView();

		initListener();

		DisplayMetrics dm = getResources().getDisplayMetrics();
		int widthPixels = dm.widthPixels;
		int heightPixels = dm.heightPixels;
		Log.i(TAG, "hdb---widthPixels:" + widthPixels + "  heightPixels:" + heightPixels);
		densityX = 1024f / (float) widthPixels;
		densityY = 600f / (float) heightPixels;
		Log.i(TAG, "hdb---densityX:" + densityX + "  densityY:" + densityY);

		sp = getSharedPreferences("satcontorl", MODE_PRIVATE);
		
		
		mScanDevices = new ScanDevices();
		startScan();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		String ip_address = sp.getString(IP_ADDRESS, null);
		if (ip_address != null) {
			etIp.setText(ip_address);
		}
	}

	private void initListener() {
		btConnect.setOnClickListener(this);
		btFind.setOnClickListener(this);
	}

	private void initView() {
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		btConnect = (Button) findViewById(R.id.bt_connect);
//		btFind = (Button) findViewById(R.id.bt_find);
//		etIp = (EditText) findViewById(R.id.et_ip);
		llInfo = (LinearLayout) findViewById(R.id.ll_info);
	}

	private void startScan() {
		new Thread() {
			public void run() {
				mIps = mScanDevices.scan();
				for (int i = 0; i < mIps.size(); i++) {
					String address = mIps.get(i);
					int num =Integer.parseInt(address.substring(address.lastIndexOf(".")+1)) ;
					IpInfo ipInfo = new IpInfo(address,num);
					ipInfos.add(ipInfo);
				}
				sortByNum(ipInfos);
				mHandler.sendEmptyMessage(SCAN_IP_OVER);
			}
		}.start();
	}
	
	/**sort by num*/
	private void sortByNum(ArrayList<IpInfo> list){
		Collections.sort(list, new Comparator<IpInfo>() {

			@Override
			public int compare(IpInfo lhs, IpInfo rhs) {
				int num =  lhs.getNum() - rhs.getNum();
				
				return num;
			}
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		int x = (int) event.getX();
		int y = (int) event.getY();
		changeX = (int) (x * densityX);
		changeY = (int) (y * densityY);
		
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:

			Log.i(TAG, "hdb----ACTION_DOWN--x:" + changeX + "  y:" + changeY);
			sendTouchData(0,changeX, changeY);
			break;
		case MotionEvent.ACTION_MOVE:
			Log.i(TAG, "hdb----ACTION_MOVE--x:" + changeX + "  y:" + changeY);
			sendTouchData(2,changeX, changeY);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			
			Log.i(TAG, "hdb----ACTION_UP--x:" + changeX + "  y:" + changeY);
			sendTouchData(1,changeX, changeY);
			break;

		default:
			break;
		}
		return true;
	}

	private static final String JACTION = "action";
	private static final String JX = "x";
	private static final String JY = "y";

	
	private void sendTouchData(int action, int x, int y) {
		if (dSocketTouch != null) {
			if (x >= 0 && x <= 1024 && y >= 0 && y <= 600) {
			//	String dataTouch = action+"x:" + x + ":" + y;
			//	Log.i(TAG, "hdb----dataTouch:" + dataTouch);
				JSONObject jObject = new JSONObject();
				try {
					jObject.put(JACTION, action);
					jObject.put(JX, x);
					jObject.put(JY, y);
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
				Log.i(TAG, "hdb----jObject:" + jObject.toString());
				byte[] jBytes = jObject.toString().getBytes();
				byte[] intToByte = (""+jBytes.length).getBytes();//intToByte(jBytes.length);
				byte[] data = new byte[jBytes.length + 2];
				System.arraycopy(intToByte, 0, data, 0, intToByte.length);
				System.arraycopy(jBytes, 0, data, intToByte.length, jBytes.length);
				Log.i(TAG, "hdb----data:" + new String(data));
				try {
					DatagramPacket dPacket = new DatagramPacket(data, data.length, host,8181);
					dSocketTouch.send(dPacket);
					dPacket = null;
					data = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
	}

	private boolean isRun = true;

	private DatagramSocket dSocketTouch;
	private DatagramSocket dSocketData;
	private void startTouchServer() {
		new Thread() {

			public void run() {
				try {
				dSocketTouch = new DatagramSocket(8181);
					Log.i(TAG, "hdb---touch--连接成功");
				//	dos = new DataOutputStream(touchSocket.getOutputStream());
				} catch (Exception e) {
					Log.e(TAG, "hdb--touchServer-ex:"+e.toString());
					mHandler.sendEmptyMessage(CONNECT_FAIL);
					
				}

			}
		}.start();

	}

	//InputStream is;

	private Button btConnect;

	
	private void startServer() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Log.i(TAG, "hdb---data--连接start");
				//	dataSocket = new Socket(serverIp, 8686);// 10.0.0.24
				//	is = dataSocket.getInputStream();
				//	DataInputStream dis = new DataInputStream(is);
					dSocketData = new DatagramSocket(8686);
					Log.i(TAG, "hdb---data--连接成功");
					mHandler.sendEmptyMessage(CONNET_SUCCESS);
					isRun = true;
					while (isRun) {
						data = new byte[1024 * 100];
						DatagramPacket pack = new DatagramPacket(data, data.length);
						dSocketData.receive(pack);
						Log.i(TAG, "hdb-----lenght:" + pack.getLength() + "   offset:" + pack.getOffset());
						onFrame(pack.getData(), pack.getOffset(), pack.getLength());
						data = null;
					}
				} catch (Exception ex) {
					Log.e(TAG, "hdb--dataServer-ex:"+ex.toString());
					//ex.toString();
					mHandler.sendEmptyMessage(CONNECT_FAIL);
				}
			}
		}).start();
	}
	

	
	

	private int failCount = 0;
	private byte[] data = null;

	private Button btFind;

	private EditText etIp;

	private ScanDevices mScanDevices;

	private ListView lvIp;

	private AlertDialog mDialog;

	private String serverIp;

	private LinearLayout llInfo;

	private ProgressBar mPBar;

	private SharedPreferences sp;

	private InetAddress host;

	public static int bufferToInt(byte[] src) {
		int value;
		value = (int) ((src[0] & 0xFF) | ((src[1] & 0xFF) << 8) | ((src[2] & 0xFF) << 16) | ((src[3] & 0xFF) << 24));
		return value;
	}

	@SuppressLint("InlinedApi") public void initDecoder() {
		try {
			mCodec = MediaCodec.createDecoderByType(MIME_TYPE);

			final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
			format.setInteger(MediaFormat.KEY_BIT_RATE, FRAME_BIT_RATE);
			format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
			format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FRAME_INTERVAL);

			// 103, 66, -128, 40, -38, 1, 16, 15, 30, 94, 1, -76, 40, 77,
			// 64],PPS=[104, -50, 6, -30
			//sps:[0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64, 4, -33, -107],PPS=[0, 0, 0, 1, 104, -50, 56, -128]

			//sps:[0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64, 4, -33, -107],PPS=[0, 0, 0, 1, 104, -50, 56, -128]
			byte[] header_sps = { 0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64, 4, -33, -107 };
			byte[] header_pps = { 0, 0, 0, 1, 104, -50, 56, -128 };

			format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
			format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
			mCodec.configure(format, mSurfaceView.getHolder().getSurface(), null, 0);
			mCodec.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean onFrame(byte[] buf, int offset, int length) {
		// Get input buffer index
		ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
		int inputBufferIndex = mCodec.dequeueInputBuffer(100);
		Log.v(TAG, " inputBufferIndex  " + inputBufferIndex);

		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
			inputBuffer.clear();
			inputBuffer.put(buf, offset, length);
			mCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
			// mCount++;
		} else {
			return false;
		}
		// Get output buffer index
		MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);

		while (outputBufferIndex >= 0) {
			mCodec.releaseOutputBuffer(outputBufferIndex, true);
			outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		System.exit(1);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
//		case R.id.bt_find:
//			Builder mBuilder = new AlertDialog.Builder(UdpActivity.this);
//			mBuilder.setTitle("Select ip address");
//			View rootView = View.inflate(UdpActivity.this, R.layout.view_ip_list, null);
//			mBuilder.setView(rootView);
//			lvIp = (ListView) rootView.findViewById(R.id.lv_ip);
//			mPBar = (ProgressBar) rootView.findViewById(R.id.progressBar1);
//			if (mIps != null && mIps.size() > 0) {
//				mPBar.setVisibility(View.GONE);
//				if (mAdapter == null){
//					setListAdapter();
//				}else {
//					mAdapter.notifyDataSetChanged();
//				}
//			}else {
//				mPBar.setVisibility(View.VISIBLE);
//			}
//			mBuilder.setOnDismissListener(new OnDismissListener() {
//				
//				@Override
//				public void onDismiss(DialogInterface dialog) {
//					Log.i(TAG, "hdb----dialog--dismiss");
//					mAdapter = null;
//				}
//			});
//			mDialog = mBuilder.show();
//			break;
		case R.id.bt_connect:
			serverIp = etIp.getText().toString().trim();
			
			if (TextUtils.isEmpty(serverIp)) {
				Toast.makeText(UdpActivity.this, "please input ip address !", Toast.LENGTH_SHORT).show();
			}
			try {
				host = InetAddress.getByName(serverIp);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			startServer();
			startTouchServer();
			initDecoder();
			break;

		default:
			break;
		}

	}

	private class MyAdapter extends BaseAdapter {

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder vHolder;
			if (convertView == null) {
				vHolder = new ViewHolder();
				convertView = View.inflate(UdpActivity.this, R.layout.list_item, null);
				vHolder.mIpItem = (TextView) convertView.findViewById(R.id.tv_list_ip);
				convertView.setTag(vHolder);
			} else {
				vHolder = (ViewHolder) convertView.getTag();
			}

			vHolder.mIpItem.setText(ipInfos.get(position).getIpAddress());
			return convertView;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public int getCount() {
			return mIps.size();
		}

	}

	
	public static byte[] intToByte(int value) {
        byte[] src = new byte[2];
        src[1] = (byte) ((value>>8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }
}
