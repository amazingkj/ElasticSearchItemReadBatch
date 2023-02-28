

package io.spring.cloud.elastic.job;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@NoArgsConstructor
@ToString
@Component
public class CustomJobParameter {

    //FTpTasklet
    private String FTP_SERVER;
    private String FTP_USER;
    private String FTP_PASSWORD;
    private String FTP_PATH;
    private String FILE_PATH;
    private String CD_FILE_PATH;

    //MakeCSVStatisticsTasklet
    private String FROM;
    private String TO;
    private String greaterThan;
    private String greaterThanDate;

    //FTpTasklet
    @Value("${batch.ftp.server}")
    public void setFTP_SERVER(String FTP_SERVER) {this.FTP_SERVER=FTP_SERVER;}
    @Value("${batch.ftp.user}")
    public void setFTP_USER(String FTP_USER) {this.FTP_USER=FTP_USER;}

    @Value("${batch.ftp.password}")
    public void setFTP_PASSWORD(String FTP_PASSWORD) {this.FTP_PASSWORD=FTP_PASSWORD;}

    @Value("${batch.ftp.path}")
    public void setFTP_PATH(String FTP_PATH) {this.FTP_PATH=FTP_PATH;}
    @Value("${batch.ftp.path2}")
    public void setCD_FILE_PATH(String CD_FILE_PATH) {this.CD_FILE_PATH=CD_FILE_PATH;}
    @Value("${batch.file.path}")
    public void setFILE_PATH(String FILE_PATH) {this.FILE_PATH=FILE_PATH;}


    //MakeCSVStatisticsTasklet
    @Value("${search.date.from}")
    public void setFrom(String FROM) {this.FROM=FROM;}

    @Value("${search.date.to}")
    public void setTo(String TO) {this.TO=TO;}

    @Value("${search.date.greaterThan}")
    public void setGreaterThan(String greaterThan) {this.greaterThan=greaterThan;}







}
