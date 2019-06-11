package com.latihanari.firebasedua;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;

import static com.google.firebase.storage.FirebaseStorage.getInstance;

public class AddPostActivity extends AppCompatActivity {

    EditText mTitleEt, mDescrEt;
    ImageView mPostIv;
    Button mUploadBtn;

    //Folder path for Firebase Storage
    String mStoragePath = "All_Image_Uploads/";
    //Root Database name for firebase database
    String mDatabasePath = "Data";

    //Creating URI
    Uri mFilePathUri;

    //Creating StorageReference and Database reference
    StorageReference mStorageReference;
    DatabaseReference mDatabaseReference;

    //ProgressDialog
    ProgressDialog mProgressDialog;

    //Image request code for choosing image
    int IMAGE_REQUEST_CODE = 5;

    //intent data will be stored in these variables
    String cTitle, cDescr,cImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_post);

        //Actionbar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Add New Post");

        mTitleEt = findViewById(R.id.pTitleEt);
        mDescrEt = findViewById(R.id.pDescrEt);
        mPostIv = findViewById(R.id.pImageIv);
        mUploadBtn = findViewById(R.id.pUploadBtn);

        //try to get data from intent if not null
        Bundle intent = getIntent().getExtras();
        if (intent !=null) {
            //get and store data
            cTitle = intent.getString("cTitle");
            cDescr = intent.getString("cDescr");
            cImage = intent.getString("cImage");
            //set this data to views
            mTitleEt.setText(cTitle);
            mDescrEt.setText(cDescr);
            Picasso.get().load(cImage).into(mPostIv);
            //change title of actionbar and button
            actionBar.setTitle("Update Post");
            mUploadBtn.setText("Update");
        }


        //image click to choose image
        mPostIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //creating intent
                Intent intent = new Intent();
                //setting intent type as image to select image from phone storage
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"Select Image"), IMAGE_REQUEST_CODE);
            }
        });
        //button click to upload data to firebase
        mUploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mUploadBtn.getText().equals("Upload")) {
                    //call method to upload data to firebase
                    uploadDataToFirebase();
                }
                else {
                    //begin update
                    beginUpdate();
                }
            }
        });

        //assign FirebaseStorage instance to storage reference object
        mStorageReference = getInstance().getReference();
        //asign FirebaseDatabase instance with root database name
        mDatabaseReference = FirebaseDatabase.getInstance().getReference(mDatabasePath);

        //progress dialog
        mProgressDialog = new ProgressDialog(AddPostActivity.this);

    }

    private void beginUpdate() {
        mProgressDialog.setMessage("Updating...");
        mProgressDialog.show();
        deletePreviousImage();
    }

    private void deletePreviousImage() {
        StorageReference mPictureRef = getInstance().getReferenceFromUrl(cImage);

        if (mStoragePath == null) {
            Toast.makeText(this, "Lokasi tidak boleh ksosong", Toast.LENGTH_SHORT).show();
       /* mmPost = new Post();
        mmPost.setPostURL(FirebaseUtils.getMyPostReference()); */
            mPictureRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    //deleted
                    Toast.makeText(AddPostActivity.this, "Previous image deleted...", Toast.LENGTH_SHORT).show();
                    //now upload new image and get it's url
                    uploadNewImage();
                }
            })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //failed
                            //get and show error message
                            Toast.makeText(AddPostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            mProgressDialog.dismiss();
                        }
                    });
        }
    }

    private void uploadNewImage() {
        //image name
        String imageName = System.currentTimeMillis()+ ".png";
        //storage reference
        StorageReference storageReference2 = mStorageReference.child(mStoragePath + imageName);
        //get bitmap from imageview
        Bitmap bitmap = ((BitmapDrawable)mPostIv.getDrawable()).getBitmap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //compress image
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();
        UploadTask uploadTask =storageReference2.putBytes(data);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                //uploaded
                Toast.makeText(AddPostActivity.this, "New image uploaded...", Toast.LENGTH_SHORT).show();

                //get url of newly uploaded image
                Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                while (!uriTask.isSuccessful());
                Uri downloadUri = uriTask.getResult();
                //now update database with new data
                updateDatabase(downloadUri.toString());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                //eror. get and show error massage
                Toast.makeText(AddPostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                mProgressDialog.dismiss();
            }
        });

    }

    private void updateDatabase(final String s) {
        //new values to update previous
        final String title = mTitleEt.getText().toString();
        final String descr = mDescrEt.getText().toString();
        FirebaseDatabase mFirebaseDatabase = FirebaseDatabase.getInstance();
        DatabaseReference mRef = mFirebaseDatabase.getReference("Data");

        Query query = mRef.orderByChild("title").equalTo(cTitle);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot ds: dataSnapshot.getChildren()) {
                    //update data
                    ds.getRef().child("title").setValue(title);
                    ds.getRef().child("search").setValue(title.toLowerCase());
                    ds.getRef().child("description").setValue(descr);
                    ds.getRef().child("image").setValue(s);
                }
                mProgressDialog.dismiss();
                Toast.makeText(AddPostActivity.this, "Database updated...", Toast.LENGTH_SHORT).show();
                //start post list activity after updating data
                startActivity(new Intent(AddPostActivity.this, PostsListActivity.class));
                finish();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void uploadDataToFirebase() {
        //check wheter filepathuri is empty or not
        if (mFilePathUri != null) {
            //setting progress bar title
            mProgressDialog.setTitle("Uploading...");
            //show progress dialog
            mProgressDialog.show();
            //create second storageReference
            StorageReference storageReference2nd = mStorageReference.child(mStoragePath + System.currentTimeMillis()+ "." + getFileExtension(mFilePathUri));

            //adding addOnSuccesListener to storageReferenced2nd
            storageReference2nd.putFile(mFilePathUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                            while (!uriTask.isSuccessful());
                            Uri downloadUri = uriTask.getResult();

                            //get title
                            String mPostTitle = mTitleEt.getText().toString().trim();
                            //get description
                            String mPostDescr = mDescrEt.getText().toString().trim();
                            //hid progress dialog
                            mProgressDialog.dismiss();
                            //show toast that image is uploaded
                            Toast.makeText(AddPostActivity.this, "Uploaded Successfully...", Toast.LENGTH_SHORT).show();
                            ImageUploadInfo imageUploadInfo = new ImageUploadInfo(mPostTitle, mPostDescr, downloadUri.toString(), mPostTitle.toLowerCase());
                            //getting image upload id
                            String imageUploadId = mDatabaseReference.push().getKey();
                            //adding image upload id's child element into databaseRefrence
                            mDatabaseReference.child(imageUploadId).setValue(imageUploadInfo);
                        }
                    })
                    //iff something goes wrong such as network failure etc
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //hide progress dialog
                            mProgressDialog.dismiss();
                            //show error toast
                            Toast.makeText(AddPostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            mProgressDialog.setTitle("Uploading");
                        }
                    });
        }
        else {
            Toast.makeText(this, "Please select image or add image name", Toast.LENGTH_SHORT).show();
        }
    }

    //method to get the selected image file extension from file path uri
    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        //returning the file extension
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_REQUEST_CODE
                && resultCode == RESULT_OK
                && data != null
                && data.getData() !=null) {
            mFilePathUri = data.getData();

            try {
                //getting selected image into bitmap
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), mFilePathUri);
                //setting bitmap into imageview
                mPostIv.setImageBitmap(bitmap);
            }
            catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}