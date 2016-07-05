#### 照片墙(利用Lrucache和DiskLruCache)
***
##### Lrucache
Lrucache(此类是在android-support-v4包中提供)，算法的主要原理是把最近使用对象用强引用存储在LinkedHashMap中，并且把最近最少使用的对象在缓存值达到预定值之前从内存中删除。

在过去，我们经常会使用一种非常流行的内存缓存技术的实现，即软引用或弱引用(SoftReference or WeakReference)。但是现在已经不推荐使用这种方式了，因为从android2.3（api level 9）开始，垃圾回收器会更倾向于回收持有软引用或弱引用的对象，这让软引用和弱引用变得不再可靠。另外，android3.0(api level 11)中，图片的数据会存储在本地内存中，因而无法用一种的预见的方式将其释放，这就有潜在的风险造成应用程序的内存溢出并崩溃。

举一个Lrucache图片的例子：

	private LruCache<String, Bitmap> mMemoryCache;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	// 获取到可用内存的最大值，使用内存超出这个值会引起OutOfMemory异常。
	// LruCache通过构造函数传入缓存值，以KB为单位。
	int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
	// 使用最大可用内存值的1/8作为缓存的大小。
	int cacheSize = maxMemory / 8;
	mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
		@Override
		protected int sizeOf(String key, Bitmap bitmap) {
			// 重写此方法来衡量每张图片的大小，默认返回图片数量。
			return bitmap.getByteCount() / 1024;
		}
	};
	}

	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (getBitmapFromMemCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}

	public Bitmap getBitmapFromMemCache(String key) {
		return mMemoryCache.get(key);
	}

	public void loadBitmap(int resId, ImageView imageView) {
	final String imageKey = String.valueOf(resId);
	final Bitmap bitmap = getBitmapFromMemCache(imageKey);
	if (bitmap != null) {
		imageView.setImageBitmap(bitmap);
	} else {
		imageView.setImageResource(R.drawable.image_placeholder);
		BitmapWorkerTask task = new BitmapWorkerTask(imageView);
		task.execute(resId);
	}
	}

	class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
		// 在后台加载图片。
		@Override
		protected Bitmap doInBackground(Integer... params) {
			final Bitmap bitmap = decodeSampledBitmapFromResource(
					getResources(), params[0], 100, 100);
			addBitmapToMemoryCache(String.valueOf(params[0]), bitmap);
			return bitmap;
		}
	}
##### DiskLrucache
防止多图OOM的核心思想是使用Lrucache技术，但是Lrucache只是管理了内存中图片的存储与释放，如果图片从内存中被移除的话，那么就需要从网络上重新加载一起图片，这显然非常耗时，因此就出现了DiskLrucache 

##### 该应用的核心思想
使用GridView加载图片，首先从内存缓存中加载，如果内存缓存中没有，就为要加载的每一个图片开启一个AsynsTask，先从磁盘缓存中加载，如果没有，就请求网络获取图片资源，并把图片资源添加在磁盘缓存中，然后在将其加入内存缓存中，最后在ImageView中展示，为了防止加载图片时，出现混论，为没有Imageview设置一个tag，该tag是以url标记的，