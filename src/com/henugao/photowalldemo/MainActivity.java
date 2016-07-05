package com.henugao.photowalldemo;

/**
 * 一个照片墙的例子，熟悉Lrucache 和 DiskLruCache的原理
 */
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.app.Activity;
import android.view.Menu;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.GridView;

public class MainActivity extends Activity {
	
	/**
	 * 用于展示照片墙的GridView
	 */
	private GridView mPhotoWall;
	
	/**
	 * GridView的适配器
	 */
	private PhotoWallAdapter mAdapter;
	
	private int mImageThumbSize;
	private int mImageThumbSpacing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageThumbSize = getResources().getDimensionPixelSize(
        		R.dimen.image_thumbnail_size);
        mImageThumbSpacing = getResources().getDimensionPixelSize(
        		R.dimen.image_thumbnail_spacing);
        mPhotoWall = (GridView) findViewById(R.id.photo_wall);
        mAdapter = new PhotoWallAdapter
        		(this,0,com.henugao.photowalldemo.domain.Images.imageThumbUrls,mPhotoWall);
        mPhotoWall.setAdapter(mAdapter);
       
        /**
         * 在oncreate中View。getWidth和view.getHeight是无法获得一个view的高度和宽度，
         * 这是因为view组件布局要在onresume中回调后完成，所以需要使用
         * getViewTreeObserver().addOnGlobalLayoutListener来获取宽度和高度，这是获得一个
         * view的宽度和高度的方法之一
         */
        mPhotoWall.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			
			@Override
			public void onGlobalLayout() {
				final int numColumns = (int) Math.floor(mPhotoWall
						.getWidth()/ (mImageThumbSize + mImageThumbSpacing));
				if(numColumns > 0) {
					int columnWidth = (int) (Math.floor(mPhotoWall.getWidth() / numColumns) 
							- mImageThumbSpacing);
					mAdapter.setImageHeight(columnWidth);
					mPhotoWall.getViewTreeObserver().removeGlobalOnLayoutListener(this);
				}
			}
		});
        
    }
    @Override
    protected void onPause() {
    	super.onPause();
    	mAdapter.flushCache();
    }
    
    @Override
    protected void onDestroy() {
      	super.onDestroy();
      	mAdapter.cancelAllTasks();
    }

    
}
