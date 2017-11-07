# Jenkins Google Drive Downloader Plugin

## usage

```
node {
    stage("download files from Google Drvie") {
        step([$class: 'org.jenkinsci.plugins.googledrivedownloader.GoogleDriveDownloaderBuilder', 
        driveFolderId: '[Google Drive folder ID]',
        clientSecretJson: '{"installed":{"client_id":"","project_id":"","auth_uri":"https://accounts.google.com/o/oauth2/auth","token_uri":"https://accounts.google.com/o/oauth2/token","auth_provider_x509_cert_url":"https://www.googleapis.com/oauth2/v1/certs","client_secret":"","redirect_uris":["urn:ietf:wg:oauth:2.0:oob","http://localhost"]}}',
        query: '[a search query of Drive API]']
        )
    }
}
```

`driveFolderId` is a folder id from which you want to download files.
`clientSecretJson` is a OAuth 2.0 token JSON string. You can get it from Google Cloud Platform (GCP). In GCP web site, you need to activate Google Drive API and create an API key. After that, download a JSON file and paste content.
`query` is a search query of Drive API (https://developers.google.com/drive/v3/web/search-parameters).

Files are downloaded in `[workspace]/googledrive` directory. 

### allow permissions in first run

Installing this plugin, configure it in pipeline, and run the job. Then browser will be automatically opened to ask you permissions. Log in and allow permissions. Only in first run, this occurs. Finally a credential file (StoredCredential) is stored in `~/.credentials/jenkins-google-drive-downloader/` directory.