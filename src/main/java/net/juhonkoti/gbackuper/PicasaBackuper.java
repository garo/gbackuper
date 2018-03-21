package net.juhonkoti.gbackuper;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.photos.PicasawebService;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.photos.*;
import com.google.gdata.util.ServiceException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class PicasaBackuper {

    private String basePath;
    private PicasawebService picasa;

    private boolean fullSync;

    public PicasaBackuper(String basePath, String clientId, String clientSecret, String refreshToken) {
        this.basePath = basePath;
        this.fullSync = false;
        try {
            String[] SCOPESArray = {"https://picasaweb.google.com/data","http://picasaweb.google.com/data/feed/api"};
            final List SCOPES = Arrays.asList(SCOPESArray);

            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(new NetHttpTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build();
            credential.setRefreshToken(refreshToken);
            credential.refreshToken();
            System.out.println("Token: "+credential.getAccessToken());

            picasa = new PicasawebService("netjuhonkoti-gbackuper-1");
            picasa.setOAuth2Credentials(credential);

        } catch (IOException e) {
            System.err.println("IOException:" + e);
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            System.err.println("Could not authenticate to PicasawebService:" + e);
            e.printStackTrace();
            throw e;
        }
    }

    public void setFullSync(boolean fullSync) {
        this.fullSync = fullSync;
    }

    public void backup(String userId) {
        try {
            List<GphotoEntry> albums = fetchAlbums(userId);
            for (GphotoEntry album : albums) {
                downloadAlbum(userId, album.getGphotoId(), album.getTitle().getPlainText());
            }
        } catch (ServiceException e) {
            System.err.println("ServiceException:" + e);
            throw new RuntimeException(e);
        }
    }

    private List<GphotoEntry> fetchAlbums(String userId) throws ServiceException {
        try {
            URL feedUrl = new URL("https://picasaweb.google.com/data/feed/api/user/" + userId + "?v=2&type=albums");
            UserFeed feed = picasa.getFeed(feedUrl, UserFeed.class);
            return feed.getEntries();
        } catch (IOException e) {
            System.err.println("IOException:" + e);
            e.printStackTrace();
        }

        return null;
    }

    private void downloadAlbum(String userId, String albumId, String albumName) throws ServiceException {
        int startIndex = 1;
        int maxResults = 500;
        int lastBatchEntries;
        int alreadyDownloaded = 0;

        do {
            List<GphotoEntry> batch = fetchBatchOfPhotos(userId, albumId, startIndex, maxResults);
            for (GphotoEntry photo : batch) {
                boolean previouslyDownloaded = downloadPhoto(albumId, albumName, this.basePath, photo);
                if (previouslyDownloaded) {
                    alreadyDownloaded++;
                } else {
                    alreadyDownloaded--;
                    if (alreadyDownloaded < 0) {
                        alreadyDownloaded = 0;
                    }
                }
            }

            lastBatchEntries = batch.size();
            startIndex += batch.size();
            System.out.println("Fetched " + batch.size() + " photos, starting at index " + startIndex);

            if (!fullSync && alreadyDownloaded > 100) {
                System.out.println("Found too many pictures which have already been downloaded from album " + albumId + ", so not going further. Turn full sync on to prevent this.");
                return;
            }
        } while(lastBatchEntries == maxResults);

    }

    private boolean downloadPhoto(String albumId, String albumName, String basePath, GphotoEntry photo) {
        File albumPath = new File(basePath + "/" + albumId);
        if (!albumPath.exists() && !albumPath.mkdirs()) {
            throw new RuntimeException("Could not create directories for " + albumPath);
        }

        File dst = new File(basePath + "/" + albumId + "/" + photo.getTitle().getPlainText().replaceAll("[^a-zA-Z0-9-_ÄäÖö\\.]", "_"));

        /*
        try {
            long fileSize = photo.getSize();
            System.out.println("Image " + dst + " should be " + fileSize + " bytes");

            if (dst.exists()) {
                if (dst.length() != fileSize) {
                    System.out.println("Invalid size of " + dst + ", redownloading");
                } else {
                    System.out.println(dst + " was already present in the filesystem");
                    return true;
                }
            }
        } catch (ServiceException e) {
            System.err.println("Error getting photo size:" + e);
            throw new RuntimeException(e);
        }
        */

        if (dst.exists()) {
            System.out.println(dst + " was already present in the filesystem");
            return true;
        }

        File albumSymlinkPath = new File(basePath + "/" + albumName.replaceAll("[^a-zA-Z0-9-_ÄäÖö\\.]", "_"));
        try {
            Files.createSymbolicLink(Paths.get(albumSymlinkPath.getAbsolutePath()), Paths.get(albumPath.getAbsolutePath()));
        } catch (java.nio.file.FileAlreadyExistsException e) {

        } catch (IOException e) {
            System.err.println("WARNING: Could not create symlink to " + albumSymlinkPath);
            System.err.println("Exception: " + e);
        }

        Path dst2 = Paths.get(basePath + "/" + albumName.replaceAll("[^a-zA-Z0-9-_ÄäÖö]", "_"));
        System.out.println("Downloading " + ((MediaContent)photo.getContent()).getUri());
        try {
            FileUtils.copyURLToFile(new URL(((MediaContent) photo.getContent()).getUri()), dst);
        } catch (MalformedURLException e) {
            System.err.println("Malformed URL: " + ((MediaContent)photo.getContent()).getUri());
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.err.println("Could not download " + ((MediaContent)photo.getContent()).getUri());
            throw new RuntimeException(e);
        }

        return false;
    }

    private List<GphotoEntry> fetchBatchOfPhotos(String userId, String albumId, int startIndex, int maxResults) throws ServiceException {
        try {
            AlbumFeed photos = picasa.getFeed(new URL("https://picasaweb.google.com/data/feed/api/user/" + userId + "/albumid/" + albumId + "?v=2&imgmax=d&start-index=" + startIndex + "&max-results=" + maxResults),
                    AlbumFeed.class);
/*
            System.out.println("size: " + photos.getPhotoEntries().size() + ", gphotos: " + photos.getEntries().size());

            List<GphotoEntry> e = photos.getEntries();
            GphotoEntry p = e.get(0);
            System.out.println("size2: " + ((PhotoEntry)p).getSize());
*/
            return photos.getEntries();
        } catch (IOException e) {
            System.err.println("IOException:" + e);
            e.printStackTrace();
        }

        return null;
    }
}
