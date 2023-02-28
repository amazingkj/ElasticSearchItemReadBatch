package io.spring.cloud.elastic.job;

import com.jcraft.jsch.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@AllArgsConstructor
public class FTpTasklet implements Tasklet {

    @Autowired
    private final CustomJobParameter jobParameter;


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
       log.info("FtpTasklet contribution: {}", contribution);

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp;

        JSch jsch = new JSch();
        log.warn("FTP_USER : {}", jobParameter.getFTP_USER());
        log.warn("FTP_SERVER :{}", jobParameter.getFTP_SERVER());

        try {
            // 세션객체 생성 ( user , host, port )
            session = jsch.getSession(jobParameter.getFTP_USER(), jobParameter.getFTP_SERVER(),22);

            // password 설정
            session.setPassword(jobParameter.getFTP_PASSWORD());
            // 세션관련 설정정보 설정
            java.util.Properties config = new java.util.Properties();
            // 호스트 정보 검사하지 않는다.
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
           // session.setConfig("StrictHostKeyChecking", "no");
            // 접속
            session.connect();
            // sftp 채널 접속
            channel = session.openChannel("sftp");//sftp?
            channel.connect();

            log.warn("FTP : Channel(id:{}) connected.", channel.getId());
           // channelSftp = (ChannelSftp) channel;
        } catch (JSchException e) {
            e.printStackTrace();
        }

        channelSftp = (ChannelSftp) channel;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        File file = new File(jobParameter.getFILE_PATH() +"statistics_daily_"+now.format(dateTimeFormatter)+".csv");

        log.warn("file : {}",file);
        log.warn("file name : {}", file.getName());

        FileInputStream in = null;
        try { // 파일을 가져와서 inputStream에 넣고 저장경로를 찾아 put
            in = new FileInputStream(file);
            log.warn("in {}", in);
            //channelSftp.cd(jobParameter.getFTP_PATH());
            log.warn(String.valueOf(channelSftp));
            channelSftp.cd(jobParameter.getCD_FILE_PATH());
            channelSftp.put(in, file.getName());
            log.warn("Uploaded: {} at {}, file path {}", file.getName(),jobParameter.getFTP_USER(),jobParameter.getCD_FILE_PATH());
        } catch (SftpException se) {
            se.printStackTrace();
        } catch (FileNotFoundException fe) {
            fe.printStackTrace();
        } finally {
            try {
                in.close();
                channelSftp.quit();
                channel.disconnect();
                session.disconnect();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }


        return RepeatStatus.FINISHED;
    }

}