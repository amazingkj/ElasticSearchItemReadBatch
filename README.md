# ElasticSearchItemReadBatch
- Batch statistics were successfully extracted using the code, but are not recommended due to very slow processing.
- Logging application > Filebeat > Kafka > Logstash > Elastic 
- Quartz > SCDF > Batch >  file 

## 1. Filebeat

### filebeat.yml

```yaml
output.kafka:
  # initial brokers for reading cluster metadata
  hosts: ["kafka1:9092", "kafka2:9092", "kafka3:9092"]

  # message topic selection + partitioning
  topic: '%{[fields.log_topic]}'
  partition.round_robin:
    reachable_only: true

  required_acks: -1
  compression: gzip
  max_retries: 3
  max_message_bytes: 1000000
```

- `partition.round_robin` : `round_robin` 설정.
    - `random`, `round_robin`, `hash` 중 파티셔닝 전략 선택 가능, 기본값은 `hash`
    - 파티션 1개 컨슈머 1개로 운영하기 때문에 큰 차이가 없으나, 클러스터로 운용할 경우 파티션에 데이터가 균등하게 배분되는 `round_robin` 이 가장 안정적일 것이기 때문에 채택
- `reachable_only` :  `true`, 접근 가능한 브로커에 접근
    - `false`, 데이터가 균등하게 배분되다가 한쪽 브로커가 사용 불가할 경우 전송 불가
    - *cf ) All partitioners will try to publish events to all partitions by default. If a partition’s leader becomes unreachable for the beat, the output might block. All partitioners support setting `reachable_only`to overwrite this behavior. If `reachable_only`is set to `true`, events will be published to available partitions only.*
- `compression` : compression codec `none`, `snappy`,  `lz4`, `gzip`중 선택. 프로듀서에서 kafka로 데이터가 압축해서 들어오는데 그 형식을 설정함
    - 기본값 **`gzip`**
- `max_message_bytes` : 메시지를 보내는 최대 바이트
    - 기본값 1000000 (bytes)
- `required_acks` : 기본값은 1, **idempotence(멱등법칙)를 위하여 -1로 설정.**
    - 0인 경우, 메세지가 카프카에 잘 전달됐는지 확인 X :  성능은 빠르나 데이터 유실 가능성 有
    - 1인 경우, leader Partition만 확인 : 메시지 소실은 없지만 중복해서 데이터를 받는 일이 생김 (적어도 한번 전송)
    - -1인 경우,  replica까지 잘 전달됐는지 확인. PID, SEQ를 헤더에 묶어서 보내고 중복 데이터일 경우 seq 확인해서 최근 데이터로 업데이트(중복 방지), 성능은 최대 20% 감소, 카프카 버전 3.0부터는 기본 설정
- `max_retries` : 전송에 실패했을때 retry 횟수
    - 기본값 3

## 2. Kafka & Logstash 연동

### logstash.conf 파일 기본 설정값

```yaml
input {
    kafka {
        bootstrap_servers => "ip:9092"
        topics => "test-log-1"
        consumer_threads => 1
    }
}
```

- `fetch_max_bytes` : 한 번에 가져올 수 있는 최대 데이터
    - 기본값 52428800 (50MB)
- `fetch_max_wait_ms` : fetch_min_bytes 이상의 메시지가 쌓일 때까지 최대 대기 시간.
    - 기본값 500 (ms)
- `fetch_min_bytes` : fetcher가 record들을 읽어 들이는 최소 byte수
    - 기본값 없음, `fetch_max_wait_ms`와 `fetch_min_bytes`는 둘 중 하나만 만족하면 데이터를 읽기 시작함.
- `heartbeat_interval_ms` : Heart Beat Thread가 Heart Beat을 보내는 간격.
    - kafka Group Coordinator 에서 컨슈머에게 신호를 보내 응답 신호인 HeartBeat를 통해 컨슈머가 동작하고 있음을 체크
    - 기본값 3000 (ms)
- `session_timeout_ms` : 브로커가 Consumer로 Heart Beat을 기다리는 최대 시간
    - 기본값 10000 (ms), `heartbeat_interval_ms` 보다는 값이 커야 함
- `max_poll_interval_ms` : 이전 poll() 호출 후 다음 호출 poll()까지 브로커가 기다리는 시간
    - 기본값 500000 (ms)
- `enable_auto_commit` : `true`인 경우 읽어온 메시지를 브로커에 바로 commit 적용하지 않고, auto_commit_interval_ms 에 정해진 주기마다 Consumer가 자동으로 Commit을 수행, Commit 하는 정보는 데이터를 어디까지 읽었는가에 대한 내용으로 kafka offset에 저장. (컨슈머 그룹별로 저장, 그룹마다 offset은 하나)
- `auto_commit_interval_ms` : 기본값 5000 (ms)

### 기본값을 많이 사용하여 설정한 이유

- 어떤 값이 적절 할 지, 운영을 해보지 않았기 때문에 최대한 기본값 활용

## 3. ElasticSearchReader

### 필수 설정

- *`ElasticsearchOperations` :* 주입받은 operation 빈을 그대로 주입
- `targetType` : 서치하고자 하는 객체 타입.
- `name` : Reader 이름 설정
- `pageSize` : 한번에 조회할 페이지 크기
- `criteria` : es로 날리고자 하는 쿼리

### 선택 설정

- `sortOptions` : 정렬 기준. searchAfter api 사용하기 위해서 반드시 필요함.
    - 설정 값이 있든 없든 `_id` 기준으로 정렬해서 서치. (설정 값 있는 경우 최우선 순위)
        - 정렬 했을때 같은 값이 있으면 중복 검색, 혹은 누락될 위험이 있음.

```java
Sort sort = Sort.by(List.of(
		Sort.Order.desc("@timestamp"),
    Sort.Order.Asc("_id")
));

// 이 경우 timestamp 기준으로 정렬 후 겹치는 값은 id 기준으로 정렬.
// 두개의 순서를 바꾸면 반대로 적용.
```

### Criteria

- 형식
    - `Criteria.where(”필드명”)._조건(”값”)_and 또는 or( Criteria….. 반복)`
- 예시

```java
// 특정 일자 이후
Criteria.where("@timestamp").greaterThan("2023-02-17T03:10:27.270Z")

// 현재 시간으로부터 5분전 까지
Criteria.where("@timestamp").greaterThan(new Date(System.currentTimeMillis() - 60 * 5 * 1000)));

// 특정 날짜만 조회
Criteria.where("@timestamp").between("2023-02-16", "2023-02-16")
또는
Criteria.where("@timestamp").is("2023-02-16")

// 문자열 포함
Criteria.where("engine").contains("nav")

...
```

### ElasticSearchItemReader 설정 예시

```java
Criteria criteria = Criteria.where("engine").exists()
                .and(Criteria.where("@timestamp").greaterThan(new Date(System.currentTimeMillis() - 60 * 5 * 1000)));
Sort sort = Sort.by(List.of(Sort.Order.desc("@timestamp")));
return ElasticsearchItemReader.<LogData>builder()
        .name("ElasticReader")
        .pageSize(1000)
        .operations(operations)
        .criteria(criteria)
        .sortOptions(sort)
        .targetType(LogData.class)
        .build();
```

### 서치하는 ES 인덱스 설정

```java
@Document(indexName = "kafka-*")
public class LogData {
	...
}
```

- 매핑하고자 하는 엔티티 위에 `Document` 어노테이션에 설정
- 별(*) 표시 붙일 경우 와일드카드로 사용 가능

### SearchAfter 설정

```java
@NonNull
    @Override
    protected Iterator<T> doPageRead() {
        PageRequest pageable = PageRequest.of(0, pageSize, sortOptions);
        CriteriaQuery query = new CriteriaQueryBuilder(criteria)
                .withPageable(pageable)
                .build();
        if (!searchAfter.isEmpty()) {
            query.setSearchAfter(searchAfter);
        }
        List<SearchHit<T>> searchHits = operations.search(query, targetType).toList();

        searchAfter.clear();
        if (!searchHits.isEmpty()) {
            searchAfter.addAll(searchHits.get(searchHits.size() - 1).getSortValues());
        }

        return searchHits.stream().map(SearchHit::getContent).iterator();
    }
```

- 페이지 값은 0으로 고정
- 페이지 검색을 한번 할때마다 searchAfter 변수 초기화
- 첫 페이지를 제외한 나머지 검색은 초기화된 searchAfter 값을 설정하여 검색

## 4. 통계 배치

- 1개 Job, 4Step 으로 구성
- 배치는 실행 성공한 뒤에 다음 Step 이 실행되기 때문에 앞선 단계가 실패하면  파일이 전송되지 않는다는 점이 깔끔하기 때문에 파일 전송 기능을 Step으로 처리했음

- 쿼리의 기준이되는 Timestamp를 between (from, to) 또는 GreaterThan으로 분기하여 argument 전달

```
search:
  date:
    from: ${from:NONE}
    to: ${to:NONE}
    greaterThan: ${greaterThan:NONE}
```

### 도커 내 **Volume 파일 경로 유의하기**

```
//docker 설정 파일

volumes:
      - ${HOST_MOUNT_PATH:-.}:${DOCKER_MOUNT_PATH:-/home/cnb/scdf}

//.bash_profile 에 환경변수 등록

export HOST_MOUNT_PATH=/home/dataflow/data-flow/jar
```

- VM 내 도커에 띄운 SCDF에서 실행되므로 도커 내 **Volume** 으로 별도 지정해준 곳을 기준으로 파일 경로를 수정해야 함
- SCDF 내 applicatione 등록 시에도 volume 기준으로 파일 경로 지정
    - 등록할때 작성할 URL >> [file:///home**`/cnb/scdf/`**elasticsearchbatch-0.0.1-SNAPSHOT.jar]
    - 실제 경로 >> [file:///home/`**user01/scdf/jar/**`elasticsearchbatch-0.0.1-SNAPSHOT.jar]



### 의존성

- SCDF를 도커에 띄우고 Influx DB, Grafana 사용

```
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-influx</artifactId>
        </dependency> 
```

```
	<dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Hoxton.SR6</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

- CSVfile 생성은 opencsv의 CSVWriter 사용
- sftp 전송 관련 의존성 추가

```
        <dependency>
            <groupId>com.opencsv</groupId>
            <artifactId>opencsv</artifactId>
            <version>5.6</version>
        </dependency>
        <dependency>
            <groupId>org.apache.sshd</groupId>
            <artifactId>sshd-sftp</artifactId>
            <version>2.9.2</version>
        </dependency>
 
```
### 데이터 집계

```java
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
        data.add(new String[]{
                "Min",
                String.valueOf(minEntry.getValue()),
                String.valueOf(minEntry.getKey())
        });

        return null;
    }
```

- 원하는 정보를 행별로 list에 추가한 뒤에 csv 파일로 작성

```java
				
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
...

				//20행
        data.add(new String[]{
                "Top 3","ENGINE","Count","Percent"
        });
        //21,22,23행
        sortTop3Iterator((HashMap) engines, "ENGINE");
							
...

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

...
			CustomCSVWriter.write("statistics_daily_"+now.format(yyyyMMdd)+".csv", data);
```

### 아쉬웠던 점

- 속도
    - 시연 당시 VM 서버가 터졌던 건 VM 메모리 부족 문제였으며 해결 후 160만건 통계 배치 실행이 완료가 가능했음. 단, 여전히 속도는 느림.
        - 62만건 집계시 8분 49초, 160만건 집계시 약 25분 ~30분 정도 소요
    - ElasticSearch의 searchafter설정이 spring 버전과 맞지 않아서 해당 설정을 3.0으로 높인 뒤 처리 속도가 개선됨
    - chunksize를 1만건 이상으로 조절하려면 elastic 에서의 별도의 설정이 필요함, 또한 속도를 높이는데에 유의미한 변화는 없었음
- 시간 설정
    - 성능 저하가 발생하자 쿼리가 발송되는 시점과 CSV 파일이 쓰여지는 시점의 시간차가 많이 발생. 통계 CSV 파일에 기입되는 시간정보에 오류가 발생한다는 점 발견
    
