package com.sat.satcontorl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import com.sat.satcontorl.R;
import com.sat.satcontorl.utils.DeviceInfo;
import com.sat.satcontorl.utils.HideSystemUIUtils;
import com.sat.satcontorl.utils.IpUtils;

public class MainActivity extends Activity implements OnClickListener {

	private final static String TAG = "H264";

	private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video

	// hdb
	private final static int VIDEO_WIDTH = 961;
	private final static int VIDEO_HEIGHT = 600;

	private final static int FRAME_RATE = 25;
	private final static int FRAME_INTERVAL = 1;
	// private final static int FRAME_BIT_RATE = 614400;
	private final static int FRAME_BIT_RATE = 1000000;
	// private final static int FRAME_BIT_RATE = 921600;

	protected static final int CONNET_SUCCESS = 1;

	protected static final int SCAN_IP_OVER = 2;

	protected static final int CLEAR_FAILCOUNT = 3;

	protected static final int CONNECT_FAIL = 5;

	protected static final int START_SHOW_DATA = 6;

	private static final int MULTIPORT = 9696;
	private static final int DATAPORT = 8686;
	private static final int TOUCHPORT = 8181;
	private static final int BACKPORT = 9191;

	// 用于屏幕数据
	private Socket dataSocket;
	// 用于触摸
	private Socket touchSocket;

	private DataOutputStream dos;

	private float densityX = 0;
	private float densityY = 0;

	private int changeX = 0;
	private int changeY = 0;

	private SurfaceView mSurfaceView;
	private MediaCodec mCodec;
	private boolean isStart = false;

	private Handler mHandler = new Handler() {

		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CONNET_SUCCESS:
				if (llInfo != null) {
					llInfo.setVisibility(View.GONE);
					lvDevice.setVisibility(View.GONE);
				}
				if (pbWait != null) {
					pbWait.setVisibility(View.VISIBLE);
					mHandler.sendEmptyMessageDelayed(START_SHOW_DATA, 5000);
				}
				break;

			case CONNECT_FAIL:
				onBackPressed();
				break;

			case CLEAR_FAILCOUNT:
				failCount = 0;
				break;
			case SCAN_IP_OVER:
				findDeviceOver();
				if (!isStart) {
					if (mAdapter == null) {
						mAdapter = new MyAdapter();
						
						lvDevice.setAdapter(mAdapter);
					//	mAdapter.notifyDataSetChanged();
					}else {
						mAdapter.notifyDataSetChanged();
					}
				}
				
				break;

			case START_SHOW_DATA:
				if (pbWait != null) {
					pbWait.setVisibility(View.GONE);
				}
				if (llInfo != null) {
					llInfo.setVisibility(View.GONE);
				}
				break;

			default:
				break;
			}
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		HideSystemUIUtils.hideSystemUI(this);
		setContentView(R.layout.activity_main);
		initView();
		initListener();
		DisplayMetrics dm = getResources().getDisplayMetrics();
		int widthPixels = dm.widthPixels;
		int heightPixels = dm.heightPixels;
		Log.i(TAG, "hdb---widthPixels:" + widthPixels + "  heightPixels:"
				+ heightPixels);
		densityX = 1024f / (float) widthPixels;
		densityY = 600f / (float) heightPixels;
		Log.i(TAG, "hdb---densityX:" + densityX + "  densityY:" + densityY);

		deviceInfos = new ArrayList<DeviceInfo>();
		fRunnable = new FindDeviceRunnable();
		mHandler.postDelayed(fRunnable, 2000);
		findDevice();
		startUdpBroadcast();

	}

	private void startUdpBroadcast() {
		new Thread() {
			public void run() {
				try {
					if (multicastSocket == null) {
						multicastSocket = new MulticastSocket(MULTIPORT);
						multicastSocket.joinGroup(broadcastAddress);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();

	}

	@Override
	protected void onStart() {
		super.onStart();
		// String ip_address = sp.getString(IP_ADDRESS, null);
		// if (ip_address != null) {
		// etIp.setText(ip_address);
		// }
		startSearchAnimation();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (deviceInfos != null) {
			deviceInfos.clear();
		}
		isStart = false;
		
	}
	/**
	 * use udp broadcast find device
	 */
	private void findDevice() {
		try {
			broadcastAddress = IpUtils
					.getBroadcastAddress(getApplicationContext());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		IpUtils.openWifiBrocast(getApplicationContext()); // for some phone can
															// not send
															// broadcast
	}

	private MulticastSocket multicastSocket;

	private class FindDeviceRunnable implements Runnable {

		@Override
		public void run() {
			new Thread() {
				public void run() {
					try {
						sendBroadCast();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();

		}

	}

	/**
	 * send udp broadcast .
	 */
	private void sendBroadCast() throws IOException {
		String ipAddress = IpUtils.getHostIP(); // get
																			// own
																			// address
		Log.i(TAG, "hdb----send---ipAddress:" + ipAddress);
		mHandler.postDelayed(fRunnable, 3000);
		if (ipAddress != null) {
			byte[] data = ("phoneip:" + ipAddress).getBytes();
			DatagramPacket packet = new DatagramPacket(data, data.length,
					broadcastAddress, MULTIPORT);
			multicastSocket.send(packet);
			receiverBack();
		}

	}

	/**
	 * receiver udp broadcast back value.
	 */
	private void receiverBack() {
		try {
			if (udpBack == null) {
				udpBack = new DatagramSocket(BACKPORT);
			}
			byte[] data = new byte[50];
			DatagramPacket pack = new DatagramPacket(data, data.length);
			udpBack.receive(pack);
			String back = new String(pack.getData(), pack.getOffset(),
					pack.getLength());
			if (back != null && back.startsWith("serverip:")) {
				String[] split = back.split(":");
				serverIp = split[1];
				
				if(!hasDeviceInfo(deviceInfos,serverIp)){
					Log.i(TAG, "hdb-------in:");
					DeviceInfo mDeviceInfo = new DeviceInfo(serverIp, split[2]);
					deviceInfos.add(mDeviceInfo);
					
					byte[] over = "over".getBytes();
					DatagramPacket packet = new DatagramPacket(over, over.length,
							broadcastAddress, MULTIPORT);
					multicastSocket.send(packet);
					mHandler.sendEmptyMessageDelayed(SCAN_IP_OVER, 2000);
					
				}
				 Log.i(TAG, "hdb-------serverIp:"+serverIp+"   split[2]:"+split[2]);
			//	mHandler.removeCallbacks(fRunnable);
				
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	
	
	

	private boolean hasDeviceInfo(ArrayList<DeviceInfo> Infos, String ip) {
		for (int i = 0; i < Infos.size(); i++) {
			if (ip != null && ip.equals(Infos.get(i).getIpAddress())) {
				return true;
			}
		}
		return false;
	}

	private void initListener() {
		btConnect.setOnClickListener(this);
		lvDevice.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				serverIp = deviceInfos.get(arg2).getIpAddress();
				Log.i(TAG, "hdb---onItemClick--serverIp:"+serverIp);
				isStart = true;
				initDecoder();
				startServer();
				startTouchServer();
				
			}
		});
		
	}
	
	private class MyAdapter extends BaseAdapter{

		@Override
		public int getCount() {
			return deviceInfos.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder vHolder;
			if (convertView == null) {
				vHolder = new ViewHolder();
				convertView = View.inflate(getApplicationContext(), R.layout.list_item, null);
				vHolder.deviceName = (TextView)convertView.findViewById(R.id.tv_list_ip);
				convertView.setTag(vHolder);
			}
			Log.i(TAG, "hdb---name:"+deviceInfos.get(position).getName());
			vHolder = (ViewHolder) convertView.getTag();
			vHolder.deviceName.setText(deviceInfos.get(position).getName());
			
			return convertView;
		}
		
		class ViewHolder{
			TextView deviceName;
		}
		
	} 

	private void initView() {
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		btConnect = (Button) findViewById(R.id.bt_connect);
		llInfo = (LinearLayout) findViewById(R.id.ll_info);
		llFind = (LinearLayout) findViewById(R.id.ll_find);
		pbWait = (ProgressBar) findViewById(R.id.pb_wait);
		ivSearch = (ImageView) findViewById(R.id.iv_search);
		
		lvDevice = (ListView) findViewById(R.id.lv_device);
	}

	private void startSearchAnimation() {
		AnimationSet as = new AnimationSet(true);
		RotateAnimation ra = new RotateAnimation(0, 90, Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 1f);
		TranslateAnimation ta = new TranslateAnimation(-50, 0, 0, -50);
		ta.setDuration(1000);
		ta.setRepeatCount(-1);
		ra.setDuration(1000);
		ra.setRepeatCount(-1);
		as.addAnimation(ta);
		as.addAnimation(ra);
		as.setRepeatMode(AnimationSet.REVERSE);
		if (ivSearch != null) {
			ivSearch.startAnimation(as);
		}
		
	}
	private void findDeviceOver() {
		llFind.setVisibility(View.GONE);
		llInfo.setVisibility(View.VISIBLE);
		
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		int x = (int) event.getX();
		int y = (int) event.getY();
		changeX = (int) (x * densityX);
		changeY = (int) (y * densityY);

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:

			// Log.i(TAG, "hdb----ACTION_DOWN--x:" + changeX + "  y:" +
			// changeY);
			sendTouchData(0, changeX, changeY);
			break;
		case MotionEvent.ACTION_MOVE:
			// Log.i(TAG, "hdb----ACTION_MOVE--x:" + changeX + "  y:" +
			// changeY);
			sendTouchData(2, changeX, changeY);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:

			// Log.i(TAG, "hdb----ACTION_UP--x:" + changeX + "  y:" + changeY);
			sendTouchData(1, changeX, changeY);
			break;

		default:
			break;
		}
		return super.onTouchEvent(event);
	}

	private static final String JACTION = "action";
	private static final String JX = "x";
	private static final String JY = "y";

	private void sendTouchData(final int action, final int x, final int y) {
		new Thread() {
			public void run() {
				if (dos != null) {
					if (x >= 0 && x <= 1024 && y >= 0 && y <= 600) {
						// String dataTouch = action+"x:" + x + ":" + y;
						// Log.i(TAG, "hdb----dataTouch:" + dataTouch);
						JSONObject jObject = new JSONObject();
						try {
							jObject.put(JACTION, action);
							jObject.put(JX, x);
							jObject.put(JY, y);
						} catch (JSONException e1) {
							e1.printStackTrace();
						}
						// Log.i(TAG, "hdb----jObject:" + jObject.toString());
						byte[] jBytes = jObject.toString().getBytes();
						// byte[] intToByte = new byte[1];//
						// (""+jBytes.length).getBytes();//intToByte(jBytes.length);
						// intToByte[0] = (byte) jBytes.length;
						byte[] intToByte = new byte[1];
						intToByte[0] = (byte) jBytes.length;
						byte[] data = new byte[jBytes.length + 1];
						System.arraycopy(intToByte, 0, data, 0, 1);
						System.arraycopy(jBytes, 0, data, 1, jBytes.length);
						// Log.i(TAG, "hdb----data:" + new String(data));
						try {
							dos.write(data);
							dos.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

				}
			}
		}.start();

	}

	private boolean isRun = true;

	private void startTouchServer() {
		new Thread() {

			public void run() {
				try {
					touchSocket = new Socket(serverIp, TOUCHPORT);
					// Log.i(TAG, "hdb---touch--连接成功");
					dos = new DataOutputStream(touchSocket.getOutputStream());
				} catch (Exception e) {
					Log.e(TAG, "hdb--touchServer-ex:" + e.toString());
					mHandler.sendEmptyMessage(CONNECT_FAIL);

				}

			}
		}.start();

	}

	InputStream is;

	private Button btConnect;

	private void startServer() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Log.i(TAG, "hdb---data--连接start");
					dataSocket = new Socket(serverIp, DATAPORT);// 10.0.0.24

					DataInputStream dis = new DataInputStream(
							dataSocket.getInputStream());
					Log.i(TAG, "hdb---data--连接成功");
					mHandler.sendEmptyMessage(CONNET_SUCCESS);
					isRun = true;
					while (isRun) {
						byte[] head = new byte[3];
						dis.read(head);
						int len = bufferToInt(head);
						Log.v(TAG, "hdb---read len " + len);

						if (len > 0 && len < 1000000) {
							byte[] buf = new byte[len];
							// Log.v(TAG, "hdb----read content " + buf.length);

							// DataInputStream dis = new DataInputStream(is);
							dis.readFully(buf);
							onFrame(buf, 0, buf.length);
							buf = null;
						} else {
							failCount++;
							if (failCount == 1) {
								mHandler.sendEmptyMessageDelayed(
										CLEAR_FAILCOUNT, 300);
							}
							if (failCount > 5) {
								failCount = 0;
								mHandler.sendEmptyMessage(CONNECT_FAIL);
							}
						}
					}
					mHandler.sendEmptyMessage(CONNECT_FAIL);
				} catch (Exception ex) {
					Log.e(TAG, "hdb--dataServer-ex:" + ex.toString());
					// ex.toString();
					mHandler.sendEmptyMessage(CONNECT_FAIL);
				}
			}
		}).start();
	}

	private int failCount = 0;
	private String serverIp;
	private LinearLayout llInfo;
	private LinearLayout llFind;
	private InetAddress broadcastAddress;

	private FindDeviceRunnable fRunnable;

	private DatagramSocket udpBack;


	private ProgressBar pbWait;

	private ImageView ivSearch;

	private ListView lvDevice;

	private ArrayList<DeviceInfo> deviceInfos;

	private MyAdapter mAdapter;

	public static int bufferToInt(byte[] src) {
		int value;
		value = (int) ((src[0] & 0xFF) | ((src[1] & 0xFF) << 8) | ((src[2] & 0xFF) << 16));
		return value;
	}

	@SuppressLint("InlinedApi")
	public void initDecoder() {
		try {
			mCodec = MediaCodec.createDecoderByType(MIME_TYPE);

			final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE,
					VIDEO_WIDTH, VIDEO_HEIGHT);
			format.setInteger(MediaFormat.KEY_BIT_RATE, FRAME_BIT_RATE);
			format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
			format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FRAME_INTERVAL);

			// 103, 66, -128, 40, -38, 1, 16, 15, 30, 94, 1, -76, 40, 77,
			// 64],PPS=[104, -50, 6, -30
			// sps:[0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64, 4, -33,
			// -107],PPS=[0, 0, 0, 1, 104, -50, 56, -128]

			// sps:[0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64, 4, -33,
			// -107],PPS=[0, 0, 0, 1, 104, -50, 56, -128]
			byte[] header_sps = { 0, 0, 0, 1, 103, 66, 64, 31, -90, -128, 64,
					4, -33, -107 };
			byte[] header_pps = { 0, 0, 0, 1, 104, -50, 56, -128 };

			format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
			format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
			mCodec.configure(format, mSurfaceView.getHolder().getSurface(),
					null, 0);
			mCodec.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean onFrame(byte[] buf, int offset, int length) {
		// Get input buffer index
		ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
		int inputBufferIndex = mCodec.dequeueInputBuffer(100);
		// Log.v(TAG, " inputBufferIndex  " + inputBufferIndex);

		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
			inputBuffer.clear();
			inputBuffer.put(buf, offset, length);
			mCodec.queueInputBuffer(inputBufferIndex, 0, length,
					System.currentTimeMillis(), 0);
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
		case R.id.bt_connect:

			if (TextUtils.isEmpty(serverIp)) {
				Toast.makeText(MainActivity.this, "please input ip address !",
						Toast.LENGTH_SHORT).show();
				return;
			}
			initDecoder();
			startServer();
			startTouchServer();

			break;

		default:
			break;
		}

	}

	public static byte[] intToByte(int value) {
		byte[] src = new byte[2];
		src[1] = (byte) ((value >> 8) & 0xFF);
		src[0] = (byte) (value & 0xFF);
		return src;
	}

	@Override
	protected void onDestroy() {
		mHandler.removeMessages(CLEAR_FAILCOUNT);
		mHandler.removeMessages(CONNECT_FAIL);
		mHandler.removeMessages(CONNET_SUCCESS);
		mHandler.removeMessages(SCAN_IP_OVER);
		mHandler.removeMessages(START_SHOW_DATA);
		super.onDestroy();
	}

}
