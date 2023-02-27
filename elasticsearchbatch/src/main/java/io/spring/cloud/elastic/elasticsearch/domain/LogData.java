package io.spring.cloud.elastic.elasticsearch.domain;

import lombok.*;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;


@Getter
@Setter
@AllArgsConstructor
@Builder
@ToString
@Document(indexName = "kafka*")
public class LogData {
    @Field(name = "@timestamp", type = FieldType.Date)
    private Date timestamp;
    @Field(name = "case", type = FieldType.Text)
    private String cases;

    private String trn_DAY;
    private String trn_DT;
    private String trn_TM;
    private String trn_ITR_GID;
    private String trn_GID;
    private String ED_DT;
    private String ed_TM;
    private String engine;
    private String api_ID;
    private String api_NM;
    private String tran_TYPE;
    private String pre_FLOW_ID;
    private String post_FLOW_ID;
    private String rule_ID;
    private String proc_STS;
    private String err_MSG;
    private String trn_DTL_SEQ;
    private String hst_PBLS_STP;
    private String dat;
    private String dat_LENGTH;
    private String source_URI;
    private String target_URL;
    private String src_METHOD_TYPE;
    private String tgt_METHOD_TYPE;
    //    private String SVR_NM;
//    private String SVR_URL;
    private String org_ID;
}
