package io.spring.cloud.elastic.util;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.util.List;

@Slf4j
public class CustomCSVWriter {


    public static int write(final String fileName, List<String[]> data) {
        int rows = 0;
        try (CSVWriter writer = new CSVWriter(new FileWriter("/home/cnb/scdf/"+fileName))) {
            writer.writeAll(data);
            rows = data.size();
            log.info("CustomCSVWriter - write: CSV 파일 생성 성공, fileName: {}", fileName);

        } catch (Exception e) {
            log.error("CustomCSVWriter - write: CSV 파일 생성 실패, fileName: {}", fileName);

        }
        return rows;

    }
}
