package com.incar.contest.elastic;

import com.alibaba.fastjson.JSON;
import com.incar.contest.util.ZonedDateTimeUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Elasticsearch
 *
 * @author Kong, created on 2020-02-27T18:27.
 * @since 1.0.0-SNAPSHOT
 */
public class Elasticsearch {

    private static Logger logger = LoggerFactory.getLogger(Elasticsearch.class);

    private static Client esClient = null;

    /**
     * ES Host,支持集群模式
     */
    private List<String> hosts;

    /**
     * ES port, 默认9300
     */
    private Integer port = 9300;

    /**
     * 集群名称， 默认elasticsearch
     */
    private String clusterName = "elasticsearch";

    /**
     * 客户端去嗅探整个集群的状态, 一般默认是true
     */
    private Boolean clientTransportSniff = true;

    public Elasticsearch(List<String> hosts, Integer port, String clusterName, Boolean clientTransportSniff) {
        this.hosts = hosts;
        this.port = port;
        this.clusterName = clusterName;
        this.clientTransportSniff = clientTransportSniff;

        try {
            if (esClient == null) {
                Settings settings = Settings.builder()
                        .put("cluster.name", clusterName)
                        .put("client.transport.sniff", clientTransportSniff)
                        .build();

                TransportClient tc = new PreBuiltTransportClient(settings);

                if(hosts.size() == 0){
                    throw new RuntimeException("Missing config entry: elasticsearch.host");
                }

                for (String host : hosts) {
                    logger.info("ElasticSearch Host: {}", host);
                    esClient = tc.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
                }
            }
        } catch(UnknownHostException ex){
            throw new RuntimeException(ex);
        }
    }

    /**
     * 保存数据
     * @param index
     * @param type
     * @param id
     * @param obj
     */
    public void save(String index, String type, String id, Object obj) {
        String json = JSON.toJSONString(obj);
        this.save(index, type, id, json);
    }

    /**
     * 保存数据
     * @param index
     * @param type
     * @param id
     * @param json
     */
    public void save(String index, String type, String id, String json) {
        esClient.prepareIndex(index, type, id).setSource(json, XContentType.JSON).get();
    }


    /**
     * 查询数据条数
     * @param index
     * @param type
     * @param map
     * @return
     */
    public Long getTotal(String index, String type, Map<String, Object> map) {
        long size = 0L;
        try {
            BoolQueryBuilder qb = new BoolQueryBuilder();
            if (null != map) {
                Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
                while(entries.hasNext()){
                    Map.Entry<String, Object> entry = entries.next();
                    qb.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                }
            }

            SearchRequestBuilder search = esClient.prepareSearch(index).
                    setTypes(type).setQuery(qb);

            SearchResponse sr = search.get();//得到查询结果
            size = sr.getHits().getTotalHits() ;
        } catch (Exception e) {
            logger.error("query is error");
        }
        return size;
    }

    /**
     * 查询对象集合信息
     * @param index 索引
     * @param type  类型
     * @param map   键值对
     * @param clazz 对象
     * @param <T> 类型
     * @return
     */
    public <T extends Object> List<T> getData(String index, String type, Map<String, Object> map, Class<T> clazz) {
        List<T> arr = new ArrayList<>();
        try {
            SortBuilder sortBuilder = SortBuilders.fieldSort("id").order(SortOrder.ASC);
            BoolQueryBuilder qb = new BoolQueryBuilder();

            if (null != map) {
                Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
                while(entries.hasNext()){
                    Map.Entry<String, Object> entry = entries.next();
                    qb.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                }
            }

            // 查询条数
            SearchRequestBuilder searchNum = esClient.prepareSearch(index)
                    .setTypes(type).setQuery(qb);
            SearchResponse srNum = searchNum.get();//得到查询结果
            int total = (int) srNum.getHits().getTotalHits();

            SearchRequestBuilder search = esClient.prepareSearch(index)
                    .setTypes(type).setQuery(qb).addSort(sortBuilder)
                    .setFrom(0)
                    .setSize(total);
            SearchResponse sr = search.get();//得到查询结果
            for(SearchHit hits:sr.getHits()){
                String json = JSON.toJSONString(hits.getSource()) ;
                T obj = JSON.parseObject(json, clazz);
                arr.add(obj) ;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return arr;
    }

    /**
     * 查询对象集合信息
     * @param index 索引
     * @param type  类型
     * @param map   键值对
     * @param timeType  时间对象类型
     * @param startDate 起始时间
     * @param endDate   终止时间
     * @param clazz 对象
     * @param <T> 类型
     * @return
     */
    public <T extends Object> List<T> getData(String index, String type, Map<String, Object> map,
                                              String timeType, Date startDate, Date endDate, Class<T> clazz) {
        List<T> arr = new ArrayList<>();
        try {
            SortBuilder sortBuilder = SortBuilders.fieldSort("id").order(SortOrder.ASC);
            BoolQueryBuilder qb = new BoolQueryBuilder();

            if (null != map) {
                Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
                while(entries.hasNext()){
                    Map.Entry<String, Object> entry = entries.next();
                    qb.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                }
            }

            if (!(StringUtils.isEmpty(timeType) || null == startDate || null == endDate)) {
                qb.must(QueryBuilders.rangeQuery(timeType)
                        .from(ZonedDateTimeUtil.dateToStr(startDate))
                        .to(ZonedDateTimeUtil.dateToStr(endDate)));
            }

            // 查询条数
            SearchRequestBuilder searchNum = esClient.prepareSearch(index)
                    .setTypes(type).setQuery(qb);
            SearchResponse srNum = searchNum.get();//得到查询结果
            int total = (int) srNum.getHits().getTotalHits();

            SearchRequestBuilder search = esClient.prepareSearch(index)
                    .setTypes(type).setQuery(qb).addSort(sortBuilder)
                    .setFrom(0)
                    .setSize(total);
            SearchResponse sr = search.get();//得到查询结果
            for(SearchHit hits:sr.getHits()){
                String json = JSON.toJSONString(hits.getSource()) ;
                T obj = JSON.parseObject(json, clazz);
                arr.add(obj) ;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return arr;
    }


    /**
     * 关闭Client
     * 不要主动使用
     */
    public synchronized void clearClient(){
        if(esClient != null){
            try {
                esClient.close();
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

}
