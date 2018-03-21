package net.juhonkoti.gbackuper;


import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.photos.*;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.photos.GphotoEntry;
import com.google.gdata.data.photos.UserFeed;
import com.google.gdata.util.ServiceException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GBackuper {

    public static void main(String[] args) {
        try {
            Map<String, String> env = System.getenv();
            String clientId = env.get("CLIENT_ID");
            String clientSecret = env.get("CLIENT_SECRET");
            String refreshToken = env.get("REFRESH_TOKEN");
            String userId = env.get("USER_ID");
            String dataPath = env.get("DATA_PATH");
            if (clientId == null || clientId.equals("")) {
                help();
            }
            if (clientSecret == null || clientSecret.equals("")) {
                help();
            }
            if (refreshToken == null || refreshToken.equals("")) {
                help();
            }
            if (userId == null || userId.equals("")) {
                help();
            }
            if (dataPath == null || dataPath.equals("")) {
                help();
            }

            do {
                PicasaBackuper picasa = new PicasaBackuper("./data", clientId, clientSecret, refreshToken);

                if (args.length > 0 && "full".equals(args[0])) {
                    System.out.println("Setting full sync on");
                    picasa.setFullSync(true);
                }

                picasa.backup(userId);

                try {
                    Thread.sleep(1000 * 3600 * 12); // update every 12 hours
                } catch (InterruptedException ignored) {}

            } while (true);

        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public static void help() {
        System.err.println("Usage: Run this app with the following ENV variables:");
        System.err.println("You first need a developer api credentials.");
        System.err.println("1) Login to https://console.developers.google.com");
        System.err.println("2) Go to Credentials section");
        System.err.println("3) Create a Service Account Key. this gives you CLIENT_ID and CLIENT_SECRET");
        System.err.println("After this you need to create an OAUTH2 refresh token where you (as a Google Picasa user)");
        System.err.println("will authorize our Service Account to access your photos");
        System.err.println("4) Open browser and go to https://accounts.google.com/o/oauth2/auth?client_id=<CLIENT_ID HERE>&redirect_uri=urn:ietf:wg:oauth:2.0:oob&scope=http://picasaweb.google.com/data/feed/api%20https://picasaweb.google.com/data%20https://www.googleapis.com/auth/photos&response_type=code");
        System.err.println("Notice that you need to fill your CLIENT_ID into the url.");
        System.err.println("5) Give your consent to the dialog and when ready you will see a new screen with a secret. Copy this");
        System.err.println("6) curl -XPOST --data \"code=<TOKEN FROM STEP 5>&client_id=<CLIENT_ID>&client_secret=<CLIENT_SECRET>&redirect_uri=urn:ietf:wg:oauth:2.0:oob&grant_type=authorization_code\" https://accounts.google.com/o/oauth2/token");
        System.err.println("The curl will return you with a JSON from which you need to save the REFRESH_TOKEN.");
        System.err.println("This token will never expire and you can now store this as REFRESH_TOKEN, along with the CLIENT_ID and CLIENT_SECRET env variables.");
        System.err.println("7) Finally you need your Picasa web album USER_ID. If you goto picasa.google.com you should find your way to url like this:");
        System.err.println("https://get.google.com/albumarchive/123456 <-- the number here is your USER_ID. That's the final part you will need.");

        System.err.println("\nTL;DR: Set the following ENV variables:");
        System.err.println("CLIENT_ID=<from step 3>");
        System.err.println("CLIENT_SECRET=<from step 3>");
        System.err.println("REFRESH_TOKEN=<from step 6>");
        System.err.println("USER_ID=<from step 7>");
        System.err.println("DATA_PATH=<where to archive the files. example: ./data>");
        System.err.println("");
        System.err.println("You can also pass the string \"full\" as the first argument, which will to a complete sync");
        System.exit(-1);
    }


}
