package com.example.signup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class ImageActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private TextView txt_total;
    private int overToatalPrice=0;

    final int UPI_PAYMENT=0;

    private Button mButtonChooseImage;
    private Button mButtonUpload;
    private TextView mTextViewShowUploads;
    private EditText mEditTextFileName;
    private ImageView mImageView;
    private ProgressBar mProgressBar;

    private Uri mImageUri;

    String approvalRefNo ="";

    private StorageReference mStorageRef;
    private DatabaseReference mDatabaseRef;

    String upiIdEt,name, note;

    private StorageTask mUploadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        mButtonChooseImage = findViewById(R.id.button_choose_image);
        mButtonUpload = findViewById(R.id.button_upload);
        mTextViewShowUploads = findViewById(R.id.text_view_show_uploads);
        mEditTextFileName = findViewById(R.id.edit_text_file_name);
        mImageView = findViewById(R.id.image_view);
        mProgressBar = findViewById(R.id.progress_bar);
        txt_total=(TextView) findViewById(R.id.txt_total);

        upiIdEt="7979757341@ybl";
        name ="Subhashish Anand";
        note ="Paying for Print";

        mStorageRef = FirebaseStorage.getInstance().getReference("uploads");
        mDatabaseRef = FirebaseDatabase.getInstance().getReference("uploads");

        mButtonChooseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser();
            }
        });

        mButtonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUploadTask != null && mUploadTask.isInProgress()) {
                    Toast.makeText(ImageActivity.this, "Upload in progress", Toast.LENGTH_SHORT).show();
                } else {
                    payUsingUpi(overToatalPrice+"",upiIdEt,name, note);
                }

            }
        });

        mTextViewShowUploads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                openImagesActivity();

            }
        });
    }

    void payUsingUpi(String amount,String upiIdEt,String name, String note){
        Uri uri=Uri.parse("upi://pay").buildUpon()
                .appendQueryParameter("pa",upiIdEt)
                .appendQueryParameter("pn", name)
                .appendQueryParameter("tn", note)
                .appendQueryParameter("am",amount)
                .appendQueryParameter("cu","INR")
                .build();

        Intent upiPayIntent =new Intent(Intent.ACTION_VIEW);
        upiPayIntent.setData(uri);

        //will always show a dialog to user to choose an app
        Intent chooser = Intent.createChooser(upiPayIntent,"Pay with");

        //check if intent resolves
        if(null!=chooser.resolveActivity(getPackageManager())){
            startActivityForResult(chooser, UPI_PAYMENT);
        }else{
            Toast.makeText(ImageActivity.this,"No UPI found",Toast.LENGTH_SHORT).show();
        }
    }


    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            mImageUri = data.getData();

            Picasso.with(this).load(mImageUri).into(mImageView);
        }

        switch (requestCode){
            case UPI_PAYMENT:
                if((RESULT_OK==resultCode)||(resultCode==11)){
                    if(data!= null){
                        String trxt = data.getStringExtra("response");
                        Log.d("msg","onActivityResult: "+trxt);
                        ArrayList<String> datalist = new ArrayList<>();
                        datalist.add(trxt);
                        upiPaymentDataOperation(datalist);
                    }else{
                        Log.d("UPI","onActivityResult: "+"Return data is null");
                        ArrayList<String> datalist = new ArrayList<>();
                        datalist.add("nothing");
                        upiPaymentDataOperation(datalist);
                    }
                }else{
                    Log.d("UPI","onActivityResult:+ "+"Return data is null"); //when user simply back without payment
                    ArrayList<String> datalist = new ArrayList<>();
                    datalist.add("nothing");
                    upiPaymentDataOperation(datalist);
                }
                break;
        }
    }

    private void upiPaymentDataOperation(ArrayList<String>data){
        if (isConnectionAvailable(ImageActivity.this)){
            String str = data.get(0);
            Log.d("UPIPAY","upiPaymentDataOperation: "+str);
            String paymentcancel="";
            if(str == null) str = "discard";
            String status="";
            String response[]= str.split("&");
            for (int i =0; i<response.length;i++){
                String equalStr[]=response[i].split("=");
                if(equalStr.length>=2){
                    if(equalStr[0].toLowerCase().equals("Status".toLowerCase())){
                        status = equalStr[1].toLowerCase();
                    }else if(equalStr[0].toLowerCase().equals("ApprovalRefNo".toLowerCase())||equalStr[0].toLowerCase().equals("txnRef".toLowerCase())){
                        approvalRefNo=equalStr[1];
                    }
                }else{
                    paymentcancel = "Payment cancelled by user";
                }
            }
            if (status.equals("success")){
                //Code to handle successful transaction here.
                Toast.makeText(ImageActivity.this, "Transaction successful",Toast.LENGTH_SHORT).show();
                Log.d("UPI", "responseStr"+approvalRefNo);
                uploadFile();
            }else if("Payment cancelled by User.".equals(paymentcancel)){
                Toast.makeText(ImageActivity.this,"Payment cancelled by user.", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(ImageActivity.this,"Transaction failed. Please Try again",Toast.LENGTH_SHORT).show();

            }
        }else{
            Toast.makeText(ImageActivity.this,"Internet connection is not available. Please check and try again",Toast.LENGTH_SHORT).show();
        }
    }
    public static boolean isConnectionAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()
                    && netInfo.isConnectedOrConnecting()
                    && netInfo.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cR.getType(uri));
    }

    private void uploadFile() {
        if (mImageUri != null) {
            final StorageReference fileReference = mStorageRef.child(System.currentTimeMillis()
                    + "." + getFileExtension(mImageUri));

            mUploadTask = fileReference.putFile(mImageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressBar.setProgress(0);
                                }
                            }, 500);


                            fileReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {


                                    Toast.makeText(ImageActivity.this, "Upload successful", Toast.LENGTH_LONG).show();
                                    Upload upload = new Upload(mEditTextFileName.getText().toString().trim(),
                                            uri.toString());

                                    //Total Cost/
                                    if(mEditTextFileName.getText().toString().trim().length()==0){
                                        overToatalPrice=overToatalPrice+2;
                                    }else {
                                        int currentpages = (Integer.valueOf(mEditTextFileName.getText().toString().trim())) * 2;
                                        overToatalPrice = overToatalPrice + currentpages;
                                    }
                                    //
                                    String uploadId = mDatabaseRef.push().getKey();
                                    mDatabaseRef.child(uploadId).setValue(upload);
                                    txt_total.setText(String.valueOf(overToatalPrice));

                                }
                            });

//
//                            Toast.makeText(MainActivity.this, "Upload successful", Toast.LENGTH_LONG).show();
//                            Upload upload = new Upload(mEditTextFileName.getText().toString().trim(),
//                                    taskSnapshot.getUploadSessionUri().toString());
//                            String uploadId = mDatabaseRef.push().getKey();
//                            mDatabaseRef.child(uploadId).setValue(upload);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(ImageActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                            mProgressBar.setProgress((int) progress);
                        }
                    });
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
        }
    }


    private void openImagesActivity() {
        Intent intent = new Intent(this, ImageSeeActivity.class);
        startActivity(intent);
    }



}
