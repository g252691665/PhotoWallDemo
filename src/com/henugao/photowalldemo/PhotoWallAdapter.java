package com.henugao.photowalldemo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import com.jakewharton.disklrucache.DiskLruCache;
import com.jakewharton.disklrucache.DiskLruCache.Snapshot;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class PhotoWallAdapter extends ArrayAdapter<String> {

	/**
	 * GraidView��ʵ��
	 */
	private GridView mPhotoWall;
	/**
	 * ��¼�����������ػ��ߵȴ����ص�����
	 */
	private Set<BitmapWorkerTask> taskCollection;
	
	/**
	 * ͼƬ���漼�������࣬���ڻ����������غõ�ͼƬ���ڳ����ڴ�ﵽ�趨ֵ�ǻὫ�������
	 * ʹ�õ�ͼƬ�Ƴ���
	 */
	private LruCache<String, Bitmap> mMemoryCache;
	/**
	 * ͼƬ���̻���ĺ�����
	 */
	private DiskLruCache mDiskLruCache;
	
	/**
	 * ��¼ÿһ������ĸ߶�
	 */
	private int mItemHeight = 0;

	public PhotoWallAdapter(Context context, int resource, String[] objects,GridView photoWall) {
		super(context, resource, objects);
		mPhotoWall = photoWall;
		taskCollection = new HashSet<BitmapWorkerTask>();
		//��ȡӦ�ó����������ڴ�
		int maxMemory = (int)Runtime.getRuntime().maxMemory();
		int cacheSize = maxMemory / 8;
		
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getByteCount();
			}
		};
		
		//��ȡͼƬ����·��
		File diskCacheDir = getDiskCacheDir(context, "thumb");
		if(!diskCacheDir.exists()) {
			diskCacheDir.mkdirs();
		}
		
		//����DislLruCachaeʵ������ʼ����������
		try {
			mDiskLruCache = DiskLruCache
					.open(diskCacheDir, getAppVersion(context), 1, 10 * 1024 *1024);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		String url = getItem(position);
		if(convertView == null) {
			convertView = View.inflate(getContext(), R.layout.photo_item, null);
		}
		ImageView imageView = (ImageView) convertView.findViewById(R.id.photo);
		if(imageView.getLayoutParams().height != mItemHeight) {
			imageView.getLayoutParams().height = mItemHeight;
		}
		//��ImageView����һ��Tag����֤һ������ͼƬʱ��������
		imageView.setTag(url);
		imageView.setImageResource(R.drawable.ic_launcher);
		loadBitmaps(imageView, url);
		return convertView;
	}
	/**
	 * ���ݴ����uniqueName��ȥӲ�̻����·����ַ
	 * @param context
	 * @param uniqueName
	 * @return
	 */
	public File getDiskCacheDir(Context context,String uniqueName) {
		String cachePath;
		if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
				|| !Environment.isExternalStorageRemovable()) {
			cachePath = context.getExternalCacheDir().getPath();
		} else {
			cachePath = context.getCacheDir().getPath();
		}
		return new File(cachePath + File.separator + uniqueName);
	}
	
	/**
	 * ��õ�ǰӦ�ó���İ汾��
	 * @param context
	 * @return
	 */
	public int getAppVersion(Context context) {
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return info.versionCode;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 1;
		
	}
	
	/**
	 * ����bitmap���󣬴˷�������Lrucache�м��������Ļ�пɼ���ImageView��bitmap
	 * ������������κ�һ��ImageView��Bitmap�����ٻ����У��ͻῪ���첽�߳�ȥ����ͼƬ
	 * @param imageView 
	 * @param imageUrl
	 */
	public void loadBitmaps(ImageView imageView,String imageUrl) {
		Bitmap bitmap = getBitmapFromMemeoryCache(imageUrl);
		if(bitmap == null) {
			BitmapWorkerTask task = new BitmapWorkerTask();
			taskCollection.add(task);
			task.execute(imageUrl);
		} else {
			if(imageView != null && bitmap != null) {
				imageView.setImageBitmap(bitmap);
			}
		}
	}
	
	/**
	 * ��Lrucache�л�ȡһ��ͼƬ����������ھͷ���null
	 * @param key 
	 * 			Lrucache�ļ������ﴫ��ͼƬ��URL��ַ
	 * @return
	 */
	public Bitmap getBitmapFromMemeoryCache(String key) {
		return mMemoryCache.get(key);
	}
	
	/**
	 * ��һ��ͼƬ��ӵ�LruCache��
	 * @param key
	 * 			LruCache�ļ������ﴫ��ͼƬ��URL��ַ
	 * @param bitmap
	 * 			LruCache�ļ������ﴫ������������ص�Bitmap����
	 */
	public void addBitmapToMemoryCache(String key,Bitmap bitmap) {
		if(getBitmapFromMemeoryCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		
		/**
		 * ͼƬ��Url��ַ
		 * 
		 */
		private String imageUrl;
		
		@Override
		protected Bitmap doInBackground(String... params) {
			imageUrl = params[0];
			FileDescriptor fileDescriptor = null;
			FileInputStream fileInputStream = null;
			try {
				String key = hashKeyForDisk(imageUrl);
				//����key��Ӧ�Ĵ��̻���
				Snapshot snapShot = mDiskLruCache.get(key);
				if(snapShot == null) {
					//���û���ҵ���Ӧ�Ļ��棬��׼�����������������ݣ���д�뻺��
					DiskLruCache.Editor editor = mDiskLruCache.edit(key);
					if(editor != null) {
						OutputStream outputStream = editor.newOutputStream(0);
						if(downloadUrlToStream(imageUrl, outputStream)) {
							editor.commit();
						}else {
							editor.abort();
						}
					}
					
					//����д����ٴβ���key��Ӧ�Ļ���
					snapShot = mDiskLruCache.get(key);
				}
				if(snapShot != null) {
					fileInputStream = (FileInputStream) snapShot.getInputStream(0);
					fileDescriptor = fileInputStream.getFD();
				}
				
				//����������ݽ�����bitmap����
				Bitmap bitmap =null;
				if(fileDescriptor != null) {
					//��Bitmap������ӵ��ڴ滺����
					bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
				}
				if(bitmap != null) {
					//��Bitmap������ӵ��ڴ滺����
					addBitmapToMemoryCache(params[0], bitmap);
				}
				return bitmap;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if(fileDescriptor == null && fileInputStream != null) {
					try {
						fileInputStream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			//����Tag�ҵ���Ӧ��ImageView�ؼ��������غõ�ͼƬ��ʾ����
			ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
			if(imageView != null && result != null) {
				imageView.setImageBitmap(result);
			}
			
			taskCollection.remove(this);
		}
		
	}
	/**
	 * ʹ��MD5�㷨�Դ����Key���м��ܲ�����
	 * @param key
	 * @return
	 */
	public String hashKeyForDisk(String key) {
		String cacheKey;
		
		try {
			MessageDigest mDigest = MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
			e.printStackTrace();
		}
		return cacheKey;
	}
	
	private String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(0XFF & bytes[i]);
			if(hex.length() == 1) {
				sb.append("0");
			}
			sb.append(hex);
		}
		return sb.toString();
	}
	/**
	 * ����http���󣬲���ȡBitmap����
	 * @param urlString
	 * 			ͼƬ��Url��ַ
	 * @param outputStream
	 * 			�������bitmap����
	 * @return
	 */
	private boolean downloadUrlToStream(String urlString,OutputStream outputStream) {
		HttpURLConnection urlConnection = null;
		BufferedOutputStream out = null;
		BufferedInputStream  in = null;
		try {
			URL url = new URL(urlString);
			urlConnection = (HttpURLConnection)url.openConnection();
			in = new BufferedInputStream(urlConnection.getInputStream(),8 * 1024);
			out = new BufferedOutputStream(outputStream,8 * 1024 );
			int b;
			while((b = in.read()) != -1) {
				out.write(b);
			}
			return true;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(urlConnection != null) {
				urlConnection.disconnect();
			}
			try {
				if(out != null) {
					out.close();
				}
				if(in != null) {
					in.close();
				}
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		return false;
		
	}
	/**
	 * ����item����ĸ߶�
	 */
	public void setImageHeight(int height) {
		if(height == mItemHeight) {
			return;
		}
		mItemHeight = height;
		notifyDataSetChanged();
	}
	
	
	/**
	 * �������¼ͬ����journal�ļ���
	 */
	public void flushCache() {
		if(mDiskLruCache != null) {
			try {
				mDiskLruCache.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
 	}
	
	/**
	 * ȡ�������������ػ�ȴ�������
	 */
	public void cancelAllTasks() {
		if(taskCollection != null) {
			for (BitmapWorkerTask task : taskCollection) {
				task.cancel(false);
			}
		}
	}
}
