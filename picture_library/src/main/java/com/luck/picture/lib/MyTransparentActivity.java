package com.luck.picture.lib;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.luck.picture.lib.compress.Luban;
import com.luck.picture.lib.compress.OnCompressListener;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.config.PictureSelectionConfig;
import com.luck.picture.lib.dialog.PictureDialog;
import com.luck.picture.lib.entity.EventEntity;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.rxbus2.RxBus;
import com.luck.picture.lib.tools.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Author：gary
 * <p/>
 * Email: xuhaozv@163.com
 * <p/>
 * description:
 * <p/>
 * Date: 2020-07-24 14:42
 */
public class MyTransparentActivity extends Activity {

    private static final int TAKE_PHOTO_CODE = 20010;

    protected PictureDialog compressDialog;
    protected PictureSelectionConfig config;
    protected String cameraPath;
    protected String originalPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            config = savedInstanceState.getParcelable(PictureConfig.EXTRA_CONFIG);
            cameraPath = savedInstanceState.getString(PictureConfig.BUNDLE_CAMERA_PATH);
            originalPath = savedInstanceState.getString(PictureConfig.BUNDLE_ORIGINAL_PATH);
        } else {
            config = PictureSelectionConfig.getInstance();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picture_empty2);
        Intent intent = new Intent(this, PictureSelectorActivity.class);
        this.startActivityForResult(intent, TAKE_PHOTO_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            //有拍照数据返回  加水印 压缩
            List<LocalMedia> selectCamera = PictureSelector.obtainMultipleResult(data);
            if (selectCamera != null && selectCamera.size() > 0) {
                compressImage(selectCamera);
            }else{
                closeActivity();
            }
        }else{
            closeActivity();
        }
    }

    protected void compressImage(final List<LocalMedia> result) {
        showCompressDialog();
        waterMark(result);
    }

    protected void waterMark(final List<LocalMedia> result) {
        //压缩图片之前看看是否需要添加水印
        if (!TextUtils.isEmpty(config.waterMark) && result != null && result.size() > 0) {
            //需要先增加水印
            Flowable.just(result)
                    .observeOn(Schedulers.io())
                    .map(new Function<List<LocalMedia>, List<LocalMedia>>() {
                        @Override
                        public List<LocalMedia> apply(@NonNull List<LocalMedia> list) throws Exception {
                            for (LocalMedia localMedia : result) {
                                if (!localMedia.isWaterMark()) {
                                    File file = new File(localMedia.getPath());
                                    boolean watermark = ImageUtils.addTextWatermark(file, config.waterMark);
                                    localMedia.setWaterMark(watermark);
                                }
                            }
                            return list;
                        }
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<List<LocalMedia>>() {
                        @Override
                        public void accept(@NonNull List<LocalMedia> files) throws Exception {
                            compressImageAfterWaterMark(result);
                        }
                    });
        } else {
            compressImageAfterWaterMark(result);
        }
    }

    protected void compressImageAfterWaterMark(final List<LocalMedia> result) {
        if (config.synOrAsy) {
            Flowable.just(result)
                    .observeOn(Schedulers.io())
                    .map(new Function<List<LocalMedia>, List<File>>() {
                        @Override
                        public List<File> apply(@NonNull List<LocalMedia> list) throws Exception {
                            List<File> files = Luban.with(MyTransparentActivity.this)
                                    .setTargetDir(config.compressSavePath)
                                    .ignoreBy(config.minimumCompressSize)
                                    .loadLocalMedia(list).get();
                            if (files == null) {
                                files = new ArrayList<>();
                            }
                            return files;
                        }
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<List<File>>() {
                        @Override
                        public void accept(@NonNull List<File> files) throws Exception {
                            handleCompressCallBack(result, files);
                        }
                    });
        } else {
            Luban.with(this)
                    .loadLocalMedia(result)
                    .ignoreBy(config.minimumCompressSize)
                    .setTargetDir(config.compressSavePath)
                    .setCompressListener(new OnCompressListener() {
                        @Override
                        public void onStart() {
                        }

                        @Override
                        public void onSuccess(List<LocalMedia> list) {
                            RxBus.getDefault().post(new EventEntity(PictureConfig.CLOSE_PREVIEW_FLAG));
                            onResult(list);
                        }

                        @Override
                        public void onError(Throwable e) {
                            RxBus.getDefault().post(new EventEntity(PictureConfig.CLOSE_PREVIEW_FLAG));
                            onResult(result);
                        }
                    }).launch();
        }
    }

    private void handleCompressCallBack(List<LocalMedia> images, List<File> files) {
        if (files.size() == images.size()) {
            for (int i = 0, j = images.size(); i < j; i++) {
                // 压缩成功后的地址
                String path = files.get(i).getPath();
                LocalMedia image = images.get(i);
                // 如果是网络图片则不压缩
                boolean http = PictureMimeType.isHttp(path);
                boolean eqTrue = !TextUtils.isEmpty(path) && http;
                image.setCompressed(eqTrue ? false : true);
                image.setCompressPath(eqTrue ? "" : path);
            }
        }
        RxBus.getDefault().post(new EventEntity(PictureConfig.CLOSE_PREVIEW_FLAG));
        onResult(images);
    }

    protected void onResult(List<LocalMedia> images) {
        dismissCompressDialog();
        Intent intent = PictureSelector.putIntentResult(images);
        setResult(RESULT_OK, intent);
        closeActivity();
    }

    protected void closeActivity() {
        finish();
        if (config.camera) {
            overridePendingTransition(0, R.anim.fade_out);
        } else {
            overridePendingTransition(0, R.anim.a3);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PictureConfig.BUNDLE_CAMERA_PATH, cameraPath);
        outState.putString(PictureConfig.BUNDLE_ORIGINAL_PATH, originalPath);
        outState.putParcelable(PictureConfig.EXTRA_CONFIG, config);
    }

    /**
     * compress loading dialog
     */
    protected void showCompressDialog() {
        if (!isFinishing()) {
            dismissCompressDialog();
            compressDialog = new PictureDialog(this);
            compressDialog.show();
        }
    }

    /**
     * dismiss compress dialog
     */
    protected void dismissCompressDialog() {
        try {
            if (!isFinishing()
                    && compressDialog != null
                    && compressDialog.isShowing()) {
                compressDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissCompressDialog();
    }
}
