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
	 * GraidView的实例
	 */
	private GridView mPhotoWall;
	/**
	 * 记录所有正在下载或者等待下载的任务
	 */
	private Set<BitmapWorkerTask> taskCollection;
	
	/**
	 * 图片缓存技术核心类，用于缓存所有下载好的图片，在程序内存达到设定值是会将最少最近
	 * 使用的图片移除掉
	 */
	private LruCache<String, Bitmap> mMemoryCache;
	/**
	 * 图片磁盘缓存的核心类
	 */
	private DiskLruCache mDiskLruCache;
	
	/**
	 * 记录每一个子项的高度
	 */
	private int mItemHeight = 0;

	public PhotoWallAdapter(Context context, int resource, String[] objects,GridView photoWall) {
		super(context, resource, objects);
		mPhotoWall = photoWall;
		taskCollection = new HashSet<BitmapWorkerTask>();
		//获取应用程序最大可用内存
		int maxMemory = (int)Runtime.getRuntime().maxMemory();
		int cacheSize = maxMemory / 8;
		
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getByteCount();
			}
		};
		
		//获取图片缓存路径
		File diskCacheDir = getDiskCacheDir(context, "thumb");
		if(!diskCacheDir.exists()) {
			diskCacheDir.mkdirs();
		}
		
		//创建DislLruCachae实例，初始化缓存数据
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
		//给ImageView设置一个Tag，保证一步加载图片时不会乱序
		imageView.setTag(url);
		imageView.setImageResource(R.drawable.ic_launcher);
		loadBitmaps(imageView, url);
		return convertView;
	}
	/**
	 * 根据传入的uniqueName或去硬盘缓存的路径地址
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
	 * 获得当前应用程序的版本号
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
	 * 加载bitmap对象，此方法会在Lrucache中检查所有屏幕中可见的ImageView的bitmap
	 * 对象，如果发现任何一个ImageView的Bitmap对象不再缓存中，就会开启异步线程去下载图片
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
	 * 从Lrucache中获取一张图片，如果不存在就返回null
	 * @param key 
	 * 			Lrucache的键，这里传入图片的URL地址
	 * @return
	 */
	public Bitmap getBitmapFromMemeoryCache(String key) {
		return mMemoryCache.get(key);
	}
	
	/**
	 * 将一张图片添加到LruCache中
	 * @param key
	 * 			LruCache的键，这里传入图片的URL地址
	 * @param bitmap
	 * 			LruCache的键，这里传入从网络上下载的Bitmap对象
	 */
	public void addBitmapToMemoryCache(String key,Bitmap bitmap) {
		if(getBitmapFromMemeoryCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		
		/**
		 * 图片的Url地址
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
				//查找key对应的磁盘缓存
				Snapshot snapShot = mDiskLruCache.get(key);
				if(snapShot == null) {
					//如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
					DiskLruCache.Editor editor = mDiskLruCache.edit(key);
					if(editor != null) {
						OutputStream outputStream = editor.newOutputStream(0);
						if(downloadUrlToStream(imageUrl, outputStream)) {
							editor.commit();
						}else {
							editor.abort();
						}
					}
					
					//缓存写入后，再次查找key对应的缓存
					snapShot = mDiskLruCache.get(key);
				}
				if(snapShot != null) {
					fileInputStream = (FileInputStream) snapShot.getInputStream(0);
					fileDescriptor = fileInputStream.getFD();
				}
				
				//将缓存的数据解析成bitmap对象
				Bitmap bitmap =null;
				if(fileDescriptor != null) {
					//将Bitmap对象添加到内存缓存中
					bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
				}
				if(bitmap != null) {
					//将Bitmap对象添加到内存缓存中
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
			//根据Tag找到相应的ImageView控件，将下载好的图片显示出来
			ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
			if(imageView != null && result != null) {
				imageView.setImageBitmap(result);
			}
			
			taskCollection.remove(this);
		}
		
	}
	/**
	 * 使用MD5算法对传入的Key进行加密并返回
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
	 * 建立http请求，并获取Bitmap对象
	 * @param urlString
	 * 			图片的Url地址
	 * @param outputStream
	 * 			解析后的bitmap对象
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
	 * 设置item子项的高度
	 */
	public void setImageHeight(int height) {
		if(height == mItemHeight) {
			return;
		}
		mItemHeight = height;
		notifyDataSetChanged();
	}
	
	
	/**
	 * 将缓存记录同步到journal文件中
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
	 * 取消所有正在下载或等待的任务
	 */
	public void cancelAllTasks() {
		if(taskCollection != null) {
			for (BitmapWorkerTask task : taskCollection) {
				task.cancel(false);
			}
		}
	}
}
