package com.she.cropimagesg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Toast;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final int REQUEST_PERMISSION_STORAGE = 0x01;
    private final int REQUEST_PERMISSION_CAMERA = 0x02;
    private final int REQUEST_CAMERA = 0x03;
    private final int REQUEST_PIC = 0x04;
    private File takeImageFile;
    private static final String TAG = "MainActivity";
    private ImageView image_view_crop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        image_view_crop = findViewById(R.id.image_view_crop);
    }

    public void showToast(String toastText) {
        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();
    }

    public boolean checkPermission(@NonNull String permission) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            boolean isAllow = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
            if (!isAllow) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_PERMISSION_CAMERA);
            }
            return isAllow;
        } else {
            return true;
        }
    }

    public void onSelectImage(View view) {
        showSelectDialog();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                showToast("权限被禁止，无法选择本地图片");
            }
        } else if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                showToast("权限被禁止，无法打开相机");
            }
        }
    }

    private SelectDialog showDialog(SelectDialog.SelectDialogListener listener, List<String> names) {
        SelectDialog dialog = new SelectDialog(this, R.style
                .transparentFrameWindowStyle,
                listener, names);
        if (!this.isFinishing()) {
            dialog.show();
        }
        return dialog;
    }

    private void showSelectDialog() {
        List<String> names = new ArrayList<>();
        names.add("拍照");
        names.add("相册");
        showDialog(new SelectDialog.SelectDialogListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: // 直接调起相机
                        /**
                         * 0.4.7 目前直接调起相机不支持裁剪，如果开启裁剪后不会返回图片，请注意，后续版本会解决
                         *
                         * 但是当前直接依赖的版本已经解决，考虑到版本改动很少，所以这次没有上传到远程仓库
                         *
                         * 如果实在有所需要，请直接下载源码引用。
                         */
                        if (checkPermission(Manifest.permission.CAMERA)) {
                            takePicture();
                        }

                        break;
                    case 1:
                        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            pickFromGallery();
                        }
                        //打开选择,本次允许选择的数量
                        break;
                    default:
                        break;
                }

            }
        }, names);
    }

    /**
     * 拍照的方法
     */
    public void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            if (Utils.existSDCard())
                takeImageFile = new File(Environment.getExternalStorageDirectory(), "/DCIM/camera/");
            else takeImageFile = Environment.getDataDirectory();
            takeImageFile = Utils.createFile(takeImageFile, "IMG_", ".jpg");
            if (takeImageFile != null) {
                // 默认情况下，即不需要指定intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                // 照相机有自己默认的存储路径，拍摄的照片将返回一个缩略图。如果想访问原始图片，
                // 可以通过dat extra能够得到原始图片位置。即，如果指定了目标uri，data就没有数据，
                // 如果没有指定uri，则data就返回有数据！

                Uri uri;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    uri = Uri.fromFile(takeImageFile);
                } else {

                    /**
                     * 7.0 调用系统相机拍照不再允许使用Uri方式，应该替换为FileProvider
                     * 并且这样可以解决MIUI系统上拍照返回size为0的情况
                     */
                    uri = FileProvider.getUriForFile(this, getString(R.string.file_provider_authorities), takeImageFile);
                    //加入uri权限 要不三星手机不能拍照
                    List<ResolveInfo> resInfoList = this.getPackageManager().queryIntentActivities
                            (takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        this.grantUriPermission(packageName, uri, Intent
                                .FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }
//                Log.e("nanchen", getString(R.string.file_provider_authorities));
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            }
        }
        startActivityForResult(takePictureIntent, REQUEST_CAMERA);
    }

    /**
     * 打开相册
     */
    private void pickFromGallery() {
        Intent intent = new Intent()
                .setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String[] mimeTypes = {"image/jpeg", "image/png"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }

        if (Build.VERSION.SDK_INT < 19) {
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else {
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.label_select_picture)), REQUEST_PIC);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PIC) {
                final Uri selectedUri = data.getData();
                if (selectedUri != null) {
                    startCrop(selectedUri);
                } else {
                    showToast("获取图片失败");
                }
            } else if (requestCode == REQUEST_CAMERA) {
                final Uri selectedUri = Uri.fromFile(takeImageFile);
                if (selectedUri != null) {
                    startCrop(selectedUri);
                } else {
                    showToast("获取图片失败");
                }
            } else if (requestCode == UCrop.REQUEST_CROP) {
                handleCropResult(data);
            }
        }
        if (resultCode == UCrop.RESULT_ERROR) {
            handleCropError(data);
        }
    }

    private void startCrop(@NonNull Uri uri) {
        String destinationFileName = "TestCropImage.jpg";
        UCrop uCrop = UCrop.of(uri, Uri.fromFile(new File(getCacheDir(), destinationFileName)));
        uCrop = uCrop.withAspectRatio(1, 1);
        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
//        options.setCompressionFormat(Bitmap.CompressFormat.PNG);
        //自定义旋转生效需要在显示源库中的底部控制view才能生效
        uCrop.useSourceImageAspectRatio();
        //1-100
        options.setCompressionQuality(90);
        //底部操作
        options.setHideBottomControls(true);
        //裁剪框是否可缩放拖动
        options.setFreeStyleCropEnabled(true);
        uCrop.withOptions(options);
        //设定大小
//        uCrop.withMaxResultSize(maxWidth, maxHeight);
        uCrop.start(MainActivity.this);
    }

    /**
     * 裁剪完得回调
     *
     * @param result
     */
    private void handleCropResult(@NonNull Intent result) {
        final Uri resultUri = UCrop.getOutput(result);
        if (resultUri != null) {
//            ResultActivity.startWithUri(this, resultUri);
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeStream(getContentResolver()
                        .openInputStream(resultUri));
                image_view_crop.setImageBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
//            image_view_crop.setImageURI(resultUri);

        } else {
            showToast("获取裁剪图片失败");
        }
    }

    private void handleCropError(@NonNull Intent result) {
        final Throwable cropError = UCrop.getError(result);
        if (cropError != null) {
            Log.e(TAG, "handleCropError: ", cropError);
            showToast(cropError.getMessage());
        } else {
            showToast("出现异常");
        }
    }
}