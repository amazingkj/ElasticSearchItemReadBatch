package io.spring.cloud.elastic.job;

import io.spring.cloud.elastic.elasticsearch.domain.LogData;
import io.spring.cloud.elastic.util.CustomCSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;

@Slf4j
@Component
public class MakeCSVStatisticsTasklet implements Tasklet {

    @Autowired
    private final CustomJobParameter jobParameter;

    public MakeCSVStatisticsTasklet(CustomJobParameter jobParameter) {
        this.jobParameter = jobParameter;
    }

    private static int total = 0;

    private final Map<String, Integer> engines = new HashMap<>();
    private final Map<String, Integer> orgs = new HashMap<>();
    private final Map<String, Integer> apids = new HashMap<>();
    private final Map<String, Integer> apinms = new HashMap<>();
    private final Map<String, Integer> srctypes = new HashMap<>();
    private final Map<String, Integer> tgttypes = new HashMap<>();
    private final Map<String, Integer> trantypes = new HashMap<>();
    private final Map<String, Integer> cases = new HashMap<>();
    private final Map<String, Integer> datLengths = new HashMap<>();

    private static final List<String[]> data = new ArrayList<>();



    public void aggregate(List<? extends LogData> logdataEntities) {


        for (LogData data : logdataEntities) {
            total += 1;

            //count, percent
            engines.put(String.valueOf(data.getEngine()), engines.getOrDefault(data.getEngine(), 0) + 1);
            orgs.put(String.valueOf(data.getOrg_ID()), orgs.getOrDefault(data.getOrg_ID(), 0) + 1);
            apids.put(String.valueOf(data.getApi_ID()), apids.getOrDefault(data.getApi_ID(),0) + 1);
            apinms.put(String.valueOf(data.getApi_NM()), apinms.getOrDefault(data.getApi_NM(),0) + 1);
            srctypes.put(String.valueOf(data.getSrc_METHOD_TYPE()), srctypes.getOrDefault(data.getSrc_METHOD_TYPE(),0) + 1);
            tgttypes.put(String.valueOf(data.getTgt_METHOD_TYPE()), tgttypes.getOrDefault(data.getTgt_METHOD_TYPE(),0) + 1);
            trantypes.put(String.valueOf(data.getTran_TYPE()), trantypes.getOrDefault(data.getTran_TYPE(),0) + 1);
            cases.put(String.valueOf(data.getCases()), cases.getOrDefault((data.getCases()),0) + 1);

            //max,min
            datLengths.put(data.getDat(), Integer.valueOf(data.getDat_LENGTH()));

        }

    }

    static String sortIterator(HashMap map, String index){
        DecimalFormat df = new DecimalFormat("#.##");
        List<Map.Entry<String, Integer>> entryList = new LinkedList<>(map.entrySet());
        entryList.sort(new Comparator<Map.Entry<String, Integer>>() {
        @Override
        public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
            return o2.getValue() - o1.getValue();
        }
       });
        for(Map.Entry<String, Integer> entry : entryList) {
            data.add(new String[]{
                    index,
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.valueOf(df.format(((double)entry.getValue()/(double) total *100)))
            });
        }
        return null;
    }


    static String sortTop3Iterator(HashMap map, String index){
        DecimalFormat df = new DecimalFormat("#.##");
        List<Map.Entry<String, Integer>> entryList = new LinkedList<>(map.entrySet());
        entryList.sort(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o1.getValue() - o2.getValue();
            }
        });
        List<Map.Entry<String, Integer>> top3 = new LinkedList<>(entryList.subList(Math.max(entryList.size() - 3, 0), entryList.size()));
        top3.sort(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue() - o1.getValue();
            }
        });

        for(Map.Entry<String, Integer> entry : top3) {
            data.add(new String[]{
                    "",
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.valueOf(df.format(((double)entry.getValue()/(double) total *100)))
            });
        }
        return null;
    }
    static String MaxMinKeyValue(HashMap map){
        Comparator<Entry<String, Integer>> comparator = new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> e1, Entry<String, Integer> e2) {
                return e1.getValue().compareTo(e2.getValue());
            }
        };

        // Max Value의 key, value
        Entry<String, Integer> maxEntry = Collections.max(map.entrySet(), comparator);

        // Min Value의 key, value
        Entry<String, Integer> minEntry = Collections.min(map.entrySet(), comparator);

        data.add(new String[]{
                "Max",
                String.valueOf(maxEntry.getValue()),
                String.valueOf(maxEntry.getKey())
        });
        //9행 Data 최소값
        data.add(new String[]{
                "Min",
                String.valueOf(minEntry.getValue()),
                String.valueOf(minEntry.getKey())
        });

        return null;
    }


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter yyyyMMddHHmm = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        SimpleDateFormat yyyyMMdd2 = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        String From="";
        String To="";
        String FromDate = jobParameter.getFROM();
        String ToDate = jobParameter.getTO();
        String greaterThan = jobParameter.getGreaterThan();


         if("NONE".equals(FromDate)||"NONE".equals(ToDate)){
             SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
             sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

             From = sdf.format(new Date(System.currentTimeMillis() - 60 * Integer.parseInt(greaterThan) * 1000));
             To = now.format(yyyyMMddHHmm);

             //Date greaterThanDate = new Date(System.currentTimeMillis() - 60 * Integer.parseInt(greaterThan) * 1000);
             //From = yyyyMMdd2.format(greaterThanDate);
             //To = now.format(yyyyMMddHHmm);

         } else if ("NONE".equals(jobParameter.getGreaterThan())) {
             From = FromDate;
             To = ToDate;

         }

         log.info("execute 내 FromDate {},ToDate {}, greaterThan {}",FromDate,ToDate,greaterThan);
         log.info("if문 지난 뒤 from {} to {}",From,To);

        //CSV 파일 내용

        //1 제목
        data.add(new String[]{
                "Elastic Search Statistics ","","",""
        });
        //2 공백
        data.add(new String[]{"   "});
        //3 조회 기간
        data.add(new String[]{
                "Aggregation Period",From,"~",To
        });
        //4
        data.add(new String[]{
                "Total Count",
                String.valueOf(total),
                "Today",
                now.format(yyyyMMdd)
        });
        //5
        data.add(new String[]{"   "});
        //6
        data.add(new String[]{
                "==========","Summary","==========","=========="
        });

        // 요청 많은 수 1~ 5등 API 정렬
        // dat_ 최대 최소값과 해당 value
        // case 별 API 1~15 percent

        //7
        data.add(new String[]{
                "Data_LENGTH","Data_LENGTH(byte)","Data"
        });
        //8,9행
        MaxMinKeyValue((HashMap) datLengths);

        //10행 - 공백
        data.add(new String[]{"   "});
        //11행
        data.add(new String[]{
                "Number of types",
                "",
                "CASES",
                String.valueOf(cases.size())
        });
        //12행
        data.add(new String[]{
                "ENGINE",
                String.valueOf(engines.size()),
                "ORG_ID",
                String.valueOf(orgs.size())
        });
        //13행
        data.add(new String[]{
                "API_ID",
                String.valueOf(apids.size()),
                "API_NM",
                String.valueOf(apinms.size())
        });


        //14행 - 공백
        data.add(new String[]{"   "});

        //15행
        data.add(new String[]{
                "Top 3","ORG_ID","Count","Percent"
        });
        //16,17,18행
        sortTop3Iterator((HashMap) orgs,"ORG_ID");
        //19행
        data.add(new String[]{"   "});
        //20행
        data.add(new String[]{
                "Top 3","ENGINE","Count","Percent"
        });
        //21,22,23행
        sortTop3Iterator((HashMap) engines, "ENGINE");

        //24행 - 공백
        data.add(new String[]{"   "});

        //25행
        data.add(new String[]{
                "Sort","Cat.","Count","Percent"
        });


        sortIterator((HashMap) cases,"CASES");
        data.add(new String[]{"   "});

        data.add(new String[]{
                "==========","Details","==========","=========="
        });

        data.add(new String[]{
                "Sort","Cat.","Count","Percent"
        });

        sortIterator((HashMap) engines, "ENGINE");
        sortIterator((HashMap) orgs,"ORG_ID");
        sortIterator((HashMap) apids,"API_ID");
        sortIterator((HashMap) apinms,"API_NM");
        sortIterator((HashMap) srctypes,"SRC_METHOD_TYPE");
        sortIterator((HashMap) tgttypes,"TGT_METHOD_TYPE");
        sortIterator((HashMap) trantypes,"TRAN_TYPE");

        data.add(new String[]{"   "});
        data.add(new String[]{"   "});

        CustomCSVWriter.write("statistics_daily_"+now.format(yyyyMMdd)+".csv", data);

        return RepeatStatus.FINISHED;

    }


}
