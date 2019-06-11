package com.latihanari.firebasedua;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;

import static com.google.firebase.storage.FirebaseStorage.getInstance;

public class PostsListActivity extends AppCompatActivity {

    LinearLayoutManager mLayoutManager; //for sorting
    SharedPreferences mSharedPref; //for saving sort settings
    RecyclerView mRecyclerView;
    FirebaseDatabase mfirebaseDatabase;
    DatabaseReference mRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_posts_list);

        //action bar
        ActionBar actionBar = getSupportActionBar();

        //set title
        actionBar.setTitle("Post List");

        mSharedPref = getSharedPreferences("SortSettings", MODE_PRIVATE);
        String mSorting = mSharedPref.getString("Sort", "newest"); //where if no settings is selected newest will be default

        if (mSorting.equals("newest")){
            mLayoutManager = new LinearLayoutManager(this);
            //this will load the items from botoom means newest firt
            mLayoutManager.setReverseLayout(true);
            mLayoutManager.setStackFromEnd(true);
        }
        else if (mSorting.equals("oldest")){
            mLayoutManager = new LinearLayoutManager(this);
            //this will load the items from botoom means oldest firt
            mLayoutManager.setReverseLayout(false);
            mLayoutManager.setStackFromEnd(false);
        }

        //recyclerview
        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);

        //set layout as linear layout
        mRecyclerView.setLayoutManager(mLayoutManager);

        //sent query to database firebase
        mfirebaseDatabase = FirebaseDatabase.getInstance();
        mRef = mfirebaseDatabase.getReference("Data");
    }

    //search data
    private void firebaseSearch(String searchText){
        //convert string entered in SearchView to lowercase
        String query = searchText.toLowerCase();

        Query firebaseSearchQuery = mRef.orderByChild("search").startAt(query).endAt(query + "\uf8ff");

        FirebaseRecyclerAdapter<Model, ViewHolder> firebaseRecyclerAdapter =
                new FirebaseRecyclerAdapter<Model, ViewHolder>(
                        Model.class,
                        R.layout.row,
                        ViewHolder.class,
                        firebaseSearchQuery
                ) {
                    @Override
                    protected void populateViewHolder(ViewHolder viewHolder, Model model, int position) {
                        viewHolder.setDetails(getApplicationContext(), model.getTitle(), model.getDescription(), model.getImage());
                    }

                    @Override
                    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

                        ViewHolder viewHolder = super.onCreateViewHolder(parent, viewType);

                        viewHolder.setOnClickListener(new ViewHolder.ClickListener() {
                            @Override
                            public void onItemClick(View view, int position) {
                                //get data from vfirebase at the position clicked
                                String mTitle = getItem(position).getTitle();
                                String mDesc = getItem(position).getDescription();
                                String mImage = getItem(position).getImage();

                                //pass this data to new activity
                                Intent intent = new Intent(view.getContext(), PostDetailActivity.class);
                                intent.putExtra("title", mTitle); // put title
                                intent.putExtra("description", mDesc); //put description
                                intent.putExtra("image", mImage); //put image url
                                startActivity(intent); //start activity

                            }

                            @Override
                            public void onItemLongClick(View view, int position) {
                                //get current title
                                final String cTittle = getItem(position).getTitle();
                                //get current description
                                final String cDescr = getItem(position).getDescription();
                                //get current image url
                                final String cImage = getItem(position).getImage();

                                //show dialog on long click
                                AlertDialog.Builder builder = new AlertDialog.Builder(PostsListActivity.this);
                                //options to display in dialog
                                String[] options = {" Update", " Delete"};
                                //set to dialog
                                builder.setItems(options, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int which) {
                                        //handle dialog item clicks
                                        if (which == 0){
                                            //updated clicked
                                            //start activity with putting current data
                                            Intent intent = new Intent(PostsListActivity.this, AddPostActivity.class);
                                            intent.putExtra("cTitle", cTittle);
                                            intent.putExtra("cDescr", cDescr);
                                            intent.putExtra("cImage", cImage);
                                            startActivity(intent);

                                        }
                                        if (which == 1){
                                            //deleted clocked
                                            //method call
                                            showDeleteDataDialog(cTittle, cImage);
                                        }
                                    }
                                });
                                builder.create().show(); //show dialog

                            }
                        });

                        return viewHolder;
                    }


                };

        //set adapter to recyclerview
        mRecyclerView.setAdapter(firebaseRecyclerAdapter);
    }

    private void showDeleteDataDialog(final String currentTittle, final String currentImage) {
        //alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(PostsListActivity.this);
        builder.setTitle("Delete");
        builder.setMessage("Are you sure to delete this post?");
        //set positive/yes button
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //user pressed "Yes", delete data

                /* whenever we publish post the parent key is automatically generated
                Since we dont know of the items to remove,
                we will first nedd to query the database to determine those key
                 */
                Query mQuery = mRef.orderByChild("title").equalTo(currentTittle);
                mQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot ds: dataSnapshot.getChildren()){
                            ds.getRef().removeValue(); //remove values from firebase where title matches
                        }
                        //show message that post (s) deleted
                        Toast.makeText(PostsListActivity.this, "Post deleted successfully...", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        //if anything goes wron get and show eror message 
                        Toast.makeText(PostsListActivity.this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                //delete image using reference of url from FirebaseStorage
                StorageReference mPictureRefe = getInstance().getReferenceFromUrl(currentImage);
                mPictureRefe.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //Delete sucessfully
                        Toast.makeText(PostsListActivity.this, "Image deleted successfully... ", Toast.LENGTH_SHORT).show();
                    }
                }) .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        //unable to delete
                        //if anything goes wrong while deleting image, get and show eror message
                        Toast.makeText(PostsListActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        //set negative/no button
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        //show dialog
        builder.create().show();
    }

    //load data into recyclerview on start
    @Override
    protected void onStart() {
        super.onStart();
      FirebaseRecyclerAdapter<Model, ViewHolder> firebaseRecyclerAdapter =
                new FirebaseRecyclerAdapter<Model, ViewHolder>(
                        Model.class,
                        R.layout.row,
                        ViewHolder.class,
                        mRef
                ) {
                    @Override
                    protected void populateViewHolder(ViewHolder viewHolder, Model model, int position) {
                        viewHolder.setDetails(getApplicationContext(), model.getTitle(), model.getDescription(), model.getImage());
                    }

                    @Override
                    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

                        ViewHolder viewHolder = super.onCreateViewHolder(parent, viewType);

                        viewHolder.setOnClickListener(new ViewHolder.ClickListener() {
                            @Override
                            public void onItemClick(View view, int position) {

                                //get data from firebase at the position clicked
                                String mTitle = getItem(position).getTitle();
                                String mDesc = getItem(position).getDescription();
                                String mImage = getItem(position).getImage();

                                //pass this data to new activity
                                Intent intent = new Intent(view.getContext(), PostDetailActivity.class);
                                intent.putExtra("title", mTitle); // put title
                                intent.putExtra("description", mDesc); //put description
                                intent.putExtra("image", mImage); //put image url
                                startActivity(intent); //start activity

                            }

                            @Override
                            public void onItemLongClick(View view, int position) {
                                //get current title
                                final String cTittle = getItem(position).getTitle();
                                //get current description
                                final String cDescr = getItem(position).getDescription();
                                //get current image url
                                final String cImage = getItem(position).getImage();

                                //show dialog on long click
                                AlertDialog.Builder builder = new AlertDialog.Builder(PostsListActivity.this);
                                //options to display in dialog
                                String[] options = {" Update", " Delete"};
                                //set to dialog
                                builder.setItems(options, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int which) {
                                        //handle dialog item clicks
                                        if (which == 0){
                                            //updated clicked
                                            //start activity with putting current data
                                            Intent intent = new Intent(PostsListActivity.this, AddPostActivity.class);
                                            intent.putExtra("cTitle", cTittle);
                                            intent.putExtra("cDescr", cDescr);
                                            intent.putExtra("cImage", cImage);
                                            startActivity(intent);

                                        }
                                        if (which == 1){
                                            //deleted clocked
                                            //method call
                                            showDeleteDataDialog(cTittle, cImage);
                                        }
                                    }
                                });
                                builder.create().show(); //show dialog

                            }
                        });

                        return viewHolder;
                    }
                };

        //set adapter to recyclerview
        mRecyclerView.setAdapter(firebaseRecyclerAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //inflate the menu; this adds items to the action bar if it present
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                firebaseSearch(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //filter as you type
                firebaseSearch(newText);
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

          /*  @Override
            public boolean onQueryTextSubmit(String query) {
                firebaseSearch(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //filter as you type
                firebaseSearch(newText);
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    } */


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //handle other action bar item clicks here
        if (id == R.id.action_sort){
            //display alert dialog to choose shorting
            showSortDialog();
            return true;
        }
        if (id == R.id.action_add){
            //display add post acivity
            startActivity(new Intent(PostsListActivity.this, AddPostActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        //options to display in dialog
        String[] sortOptions = {"Newest", "Oldest"};
        //create alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sort by")
                .setIcon(R.drawable.ic_sort_black_24dp) //set icon
                .setItems(sortOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        // The 'which' argument contains the index of the selected item
                        // 0 means "Newest" and 1 means "oldest"
                        if (which==0) {
                            //sort by newest
                            //Edit our shared preferences
                            SharedPreferences.Editor editor = mSharedPref.edit();
                            editor.putString("Sort", "newest"); //Where 'Sort' is key & 'newest' is value
                            editor.apply(); // apply/save the value in our Shared preferences
                            recreate(); //restart activity to take effect
                        }
                        else if (which==1){
                            //sort by oldest
                            SharedPreferences.Editor editor = mSharedPref.edit();
                            editor.putString("Sort", "oldest"); //Where 'Sort' is key & 'oldest' is value
                            editor.apply(); // apply/save the value in our Shared preferences
                            recreate(); //restart activity to take effect
                        }

                    }
                });
        builder.show();
    }

}
