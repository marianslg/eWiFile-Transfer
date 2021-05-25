package com.example.root.ewifiletransfer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

public class Finish extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.finish);

        ArrayList<String> _return = getIntent().getStringArrayListExtra("return");
        String textPath = getIntent().getStringExtra("textPathDownload");

        Log.e("Finish", _return.toString());

        TextView textResult = this.findViewById(R.id.textResult);
        TextView textTotalFiles = this.findViewById(R.id.textTotalFiles);
        TextView textTime = this.findViewById(R.id.textTime);
        TextView textUnitTime = this.findViewById(R.id.textUnitTime);
        TextView textVelocity = this.findViewById(R.id.textVelocity);
        TextView textUnitVelocity = this.findViewById(R.id.textUnitVelocity);
        TextView textPathDownload = this.findViewById(R.id.textPathDownload);

        if (_return.get(0).equals("OK")) {
            textResult.setText("¡La transferencia ha terminado exitosamente!");
            textResult.setTextColor(Color.parseColor("#339933"));
        } else {
            textResult.setText("No se ha finalizado la transferencia");
            textResult.setTextColor(Color.parseColor("#FF0000"));
        }

        textTotalFiles.setText(_return.get(1));
        textTime.setText(_return.get(2));
        textUnitTime.setText(_return.get(3));

        String vel = _return.get(4);

        if (!vel.equals("-")) {
            textVelocity.setText(vel.substring(0, vel.indexOf(" ")));
            textUnitVelocity.setText(vel.substring(vel.indexOf(" ") + 1, vel.length()));
        } else {
            textVelocity.setText(vel);
        }

        if(textPath.equals("NO")){
            textPathDownload.setText("");
        }

        //textPathDownload.setText(_return.get(5));
    }

    public void exit(View view) {
        finish();
        System.exit(0);
    }
}
