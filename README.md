# product-la

#Welcome to WSO2 Log Analyzer

WSO2 log analyzer is a complete log analysis solution built on top of WSO2 DAS features. Dashboarding , reporting and alerting are the popular features of log analyzer.

## Log API

|Description | Publish log event
|------------|-------
|HTTP Method | POST
|Resource Path | /logs/publish 
|Request/Response Format | application/json

Sample CURL Request 

##### payload ##
```json
{
 "@logstream"  : "['ESB', 'node-01']",
 "@timestamp" : "2013-11-28T17:01:32.003Z",
 "message"    : "10.0.0.1 - - [28/Nov/2013:11:01:31 -0600] \"GET / HTTP/1.1\" 200 303 \"-\" \"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/30.0.1599.114 Chrome/30.0.1599.114 Safari/537.36\"",
 "type"       : "nginx-access",
 "host"       : "lanyonm-linux",
 "path"       : "/var/log/nginx/access.log",
 "clientip"   : "10.0.0.1",
 "ident"      : "-",
 "auth"       : "-",
 "timestamp"  : "28/Nov/2013:11:01:31 -0600",
 "verb"       : "GET",
 "request"    : "/",
 "httpversion": "1.1",
 "response"   : "200",
 "bytes"      : "303",
 "referrer"   : "\"-\"",
 "agent"      : "\"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/30.0.1599.114 Chrome/30.0.1599.114 Safari/537.36\""
 
}
```

```
curl -X POST -H "Content-Type: application/json" -H "Authorization: Basic YWRtaW46YWRtaW4="  -d @'<JSON_PAYLOAD>' -k -v https://<LOGANALYZER_HOST>:<LOGANALYZER_HTTPS_PORT>/api/logs/publish
```
Note that it is mandatory to set the `@logstream` field in the event, otherwise all the events will get stored  under a default log category.
This `logstream` field is stored as a facet data type. You could read more about facet data type from [facet data type documentation] (https://docs.wso2.com/display/DAS300/Searching+Data+By+Categories#SearchingDataByCategories-FacetsFacets)

Basic Authorization header is in the form of base64encoded  username:password

## publish log events using logstash 

you can use http out put plugin to publish log events to log analyzer

http Example

```
    http{
         http_method => "post"
         url => "http://localhost:9763/api/logs/publish"
         headers => ["Authorization", "Basic YWRtaW46YWRtaW4="]
    }
```

Optionally you could transfer events via ssl using corresponding logstash configurations

## Timestamp configuration

Inorder to use time range search queries, you should configure a proper timestamp in to the log event. You can use the default @timestamp in logstash.
In case the log timestamp is not ISO8601 compatible, you can convert the time stamp to a long value and set it in @timestamp_long field
 
#### Converting a timestamp to a long value

```
filter {
    ruby {
        code => "
                # yyyy-MM-dd HH:mm:ss
                event['@timestamp_long'] = Time.parse(event['message']).to_i
    
        "
    }
}
```
