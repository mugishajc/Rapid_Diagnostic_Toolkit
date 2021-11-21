package org.rdtoolkit;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.zeugmasolutions.localehelper.LocaleAwareCompatActivity;

import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ClassifierMode;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.ui.home.HomeViewModel;
import org.rdtoolkit.ui.provision.ProvisionViewModel;
import org.rdtoolkit.util.InjectorUtils;

import java.util.Locale;
import java.util.UUID;

import static org.rdtoolkit.support.interop.RdtIntentBuilder.ACTION_TEST_CAPTURE;
import static org.rdtoolkit.support.interop.RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID;

public class MainActivity extends LocaleAwareCompatActivity {

    private static final int ACTIVITY_PROVISION = 1;

    private AppBarConfiguration mAppBarConfiguration;

    private HomeViewModel homeViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //set default language to kinyarwanda instead of english

        String lang = "rw";
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());



        homeViewModel =
                new ViewModelProvider(this,
                        InjectorUtils.Companion.provideHomeViewModel(this))
                        .get(HomeViewModel.class);


        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }


    public void simulateTestRequest(View view) {
        RdtIntentBuilder builder = homeViewModel.getAppRepository().getDemoIntentBuilder();
        Intent i = builder.build();

        this.startActivityForResult(i, ACTIVITY_PROVISION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == ACTIVITY_PROVISION && resultCode == RESULT_OK) {
            TestSession session = RdtUtils.getRdtSession(data);
            System.out.println(String.format("Test will be available to read at %s", session.getTimeResolved().toString()));
        }
    }

    public void goToCapture(View view) {
        String sessionId = (String)view.getTag();
        Intent captureActivity = new Intent();
        captureActivity.setAction(ACTION_TEST_CAPTURE);
        captureActivity.putExtra(INTENT_EXTRA_RDT_SESSION_ID, sessionId);
        this.startActivityForResult(captureActivity, ACTIVITY_PROVISION);
    }
}