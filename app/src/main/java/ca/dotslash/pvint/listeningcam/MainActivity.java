package ca.dotslash.pvint.listeningcam;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class MainActivity extends ListActivity {

    SimpleCursorAdapter adapter;

    final Uri mediaSrc = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] from = {
                MediaStore.MediaColumns.TITLE};
        int[] to = {
                android.R.id.text1};

        Cursor cursor = managedQuery(
                mediaSrc,
                null,
                null,
                null,
                MediaStore.Audio.Media.TITLE);

        adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, cursor, from, to);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(position);

        String _id = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

        Uri playableUri
                = Uri.withAppendedPath(mediaSrc, _id);

        Toast.makeText(this, "Play: " + playableUri.toString(), Toast.LENGTH_LONG).show();

        Intent intent = new Intent();
        intent.setClass(MainActivity.this,
                PlayerActivity.class);
        intent.setData(playableUri);
        startActivity(intent);
    }


}