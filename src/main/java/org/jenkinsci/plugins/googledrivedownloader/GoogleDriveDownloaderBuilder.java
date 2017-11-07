package org.jenkinsci.plugins.googledrivedownloader;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * File downloader from Google Drive.
 * <p>
 * You can specify a query for target files.
 * </p>
 */
public class GoogleDriveDownloaderBuilder extends Builder implements SimpleBuildStep {

    /**
     * Global instance of the scopes required by this application.
     * <p>
     * If modifying these scopes, delete your previously saved credentials at ~/.credentials/jenkins-google-drive-downloader
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

    /**
     * Directory to store user credentials for this application.
     */
    private final FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File(System.getProperty("user.home"), ".credentials/jenkins-google-drive-downloader"));

    private final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    private final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

    private final String driveFolderId;
    private final String clientSecretJson;
    private final String query;

    /**
     * Primary constructor.
     * @param driveFolderId Google Drive folder Id for download
     * @param clientSecretJson OAuth 2.0 token JSON file of Google API
     * @param query a search query of Drive API
     * @throws GeneralSecurityException can't instantiate GoogleNetHttpTransport
     * @throws IOException can't instantiate GoogleNetHttpTransport
     */
    @DataBoundConstructor
    public GoogleDriveDownloaderBuilder(String driveFolderId, String clientSecretJson, String query) throws GeneralSecurityException, IOException {
        this.driveFolderId = driveFolderId;
        this.clientSecretJson = clientSecretJson;
        this.query = query;
    }

    /**
     * Download files that match query from Google Drive.
     *
     * @param build     from Jenkins
     * @param workspace from Jenkins
     * @param launcher  from Jenkins
     * @param listener  from Jenkins
     * @throws IOException          can't access Google Drive API
     * @throws InterruptedException can't get absolute path of Jenkins workspace
     */
    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        String downloadDirectory = workspace.absolutize() + "/googledrive/";
        mkdir(downloadDirectory);
        Drive service = getDriveService();
        List<File> files = getFileInfoFromGoogleDrive(service);
        if (files.isEmpty()) {
            listener.getLogger().println("no files in Google Drive.");
            return;
        }
        downloadFilesFromGoogleDrive(downloadDirectory, service, files);
    }

    private void downloadFilesFromGoogleDrive(String downloadDirectory, Drive service, List<File> files) throws IOException {
        for (File file : files) {
            OutputStream out = new FileOutputStream(downloadDirectory + "/" + file.getName());
            Drive.Files.Get request = service.files().get(file.getId());
            request.executeMediaAndDownloadTo(out);
        }
    }

    private List<File> getFileInfoFromGoogleDrive(Drive service) throws IOException {
        FileList result = service.files().list().setQ(query)
                .setPageSize(100)
                .setOrderBy("modifiedTime")
                .setFields("files(id, name, modifiedTime, parents)")
                .execute();
        return result.getFiles() == null ? Collections.emptyList() : result.getFiles();
    }

    private void mkdir(String directory) throws IOException {
        Path p = Paths.get(directory);
        if (Files.exists(p)) {
            return;
        }
        Files.createDirectory(p);
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    private Credential authorize() throws IOException {

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(jsonFactory, new StringReader(clientSecretJson));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, jsonFactory, clientSecrets, SCOPES)
                        .setDataStoreFactory(dataStoreFactory)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
     * Build and return an authorized Drive client service.
     *
     * @return an authorized Drive client service
     * @throws IOException
     */
    private Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                httpTransport, jsonFactory, credential)
                .setApplicationName("GoogleDriveDownloaderBuilder")
                .build();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "download files from Google Drive";
        }
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public String getClientSecretJson() {
        return clientSecretJson;
    }

    public String getQuery() {
        return query;
    }

}

