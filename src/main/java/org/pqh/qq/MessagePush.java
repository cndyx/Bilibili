package org.pqh.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.DateUtils;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.pqh.entity.Bangumi;
import org.pqh.entity.Bdu;
import org.pqh.entity.Param;
import org.pqh.entity.Tsdm;
import org.pqh.task.DynamicTimer;
import org.pqh.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

import static org.pqh.util.SpringContextHolder.bduDao;
import static org.pqh.util.SpringContextHolder.biliDao;

/**
 * 信息推送
 * Created by reborn on 2017/3/29.
 */
@Component
public class MessagePush{
    private static Logger log = Logger.getLogger(MessagePush.class);


    public static List<Tsdm> todayTsdms;

    private static Map<String, Map<String, String>> pushUrl = new HashMap<>();

    public void put(String animeName,String title, String videoSite, String url) {
        Map<String, String> map =pushUrl.get(animeName);
        if(map==null){
            map=new HashMap<>();
        }
        String value = map.get(videoSite);
        if (value == null) {
            DoSoming.messagePush(animeName+"\t"+title+"\t已更新,传送门："+url);
            map.put(videoSite, url);
            pushUrl.put(animeName, map);
        }
    }

    //    @Resource
//    private BiliDao biliDao;
    @Scheduled(cron = "0 0 0/1 * * ?")
    public void acgdoge() {


        Document document = null;
        int count = 0;
        while (document == null && count < 3) {
            document = CrawlerUtil.jsoupGet(ApiUrl.acgdoge.getUrl(), CrawlerUtil.DataType.domcument, Connection.Method.GET);
            count++;
        }

        Param param = biliDao.selectParam("acgdoge").get(0);
        int a = 0;
        int b = Integer.parseInt(param.getValue());

        Elements articles = document.select("#content>article");
        List<java.lang.String> msgs = new ArrayList<>();
        for (int i = articles.size() - 1; i >= 0; i--) {
            Element article = articles.get(i);
            Element title = article.select("h2.post_h>a").first();

            String href = title.attr("href");
            a = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
            if (a > b) {
                String type = article.select("span.post_ct").text();
                List<String> tags = new ArrayList<>();
                for (Element e : article.select("div.post_tag>a")) {
                    tags.add(e.text());
                }
                String msg = type + "\t\"" + title.text() + "\"\t传送门：" + href + "\n标签：" + tags;
                log.info(msg);
                msgs.add(msg);
            }
        }
        if (DoSoming.flag.get("acgdoge")) {
            DoSoming.messagePush(msgs);
        }
        param.setValue(a + "");
        biliDao.updateParam(param);
    }

    @Scheduled(cron = "0 0/10 * * * ?")
    public void ithome() {

        Document document = null;
        int count = 0;
        while (document == null && count < 3) {
            document = CrawlerUtil.jsoupGet(ApiUrl.ithome.getUrl(), CrawlerUtil.DataType.domcument, Connection.Method.GET);
            count++;
        }

        Param param = biliDao.selectParam("ithome").get(0);
        int a = 0;
        int b = Integer.parseInt(param.getValue());

        Elements newList = document.select("div.block.new-list-1>ul:eq(0)>li.new>span.title>a");
        List<String> msgs = new ArrayList<>();
        for (int i = newList.size() - 1; i >= 0; i--) {
            Element _new = newList.get(i);
            String href = _new.attr("href");
            String text = _new.text();
            a = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1).replaceAll("\\D+", ""));
            if (a > b) {
                String msg = text + "\t传送门：" + href;
                log.info(msg);
                msgs.add(msg);
            }
        }
        if (DoSoming.flag.get("ithome")) {
            DoSoming.messagePush(msgs);
        }
        param.setValue(a + "");
        biliDao.updateParam(param);
    }

    public void parseYoukuId(){
        Tsdm tsdm=new Tsdm();
        tsdm.setCopyright("优酷");
        List<Tsdm> tsdms=bduDao.selectTsdm(tsdm);
        Document document = CrawlerUtil.jsoupGet(ApiUrl.youkuBangumi.getUrl(), CrawlerUtil.DataType.domcument, Connection.Method.GET);
        for(Tsdm t:tsdms) {
            Elements as=null;
            String name=t.getAnimeName();
            do {
                as = document.select(".part02_list li:contains(" + name + ")");
                if(as.size()!=1){
                    log.info(as);
                    log.info("无法准确从优酷新番放松表找到这部番剧："+name+"请输入别名");
                    Scanner sc=new Scanner(System.in);
                    name=sc.nextLine();
                }else{
                    break;
                }
            }while (true);
            String link=as.select("a").attr("href");
            Document document1=CrawlerUtil.jsoupGet(link,CrawlerUtil.DataType.domcument, Connection.Method.GET);
            link=document1.select(".p-thumb>a").attr("href");
            String youku_id=link.substring(link.lastIndexOf("_")+1,link.indexOf("=="));
            t.setYoukuId(youku_id);
            bduDao.updateTsdm(t);
        }
    }

    public void parseYouku(Tsdm tsdm) {
        if(tsdm.getYoukuId()==null){
            log.info(tsdm.getAnimeName()+"\tyoukuId没有入库无法获取番剧更新信息");
            return;
        }
        JsonNode jsonNode=CrawlerUtil.jsoupGet(ApiUrl.youku.getUrl(tsdm.getYoukuId()),CrawlerUtil.DataType.json, Connection.Method.GET);
        jsonNode=jsonNode.get("data").get("videos").get("list");
        jsonNode=jsonNode.get(jsonNode.size()-1);
        String title=jsonNode.get("title").asText();
        String encodevid=jsonNode.get("encodevid").asText();
        String url=ApiUrl.youkuPlay.getUrl(encodevid);

        if(StringUtils.isEmpty(tsdm.getYoukuUrl())||!tsdm.getYoukuUrl().equals(url)){
            tsdm.setYoukuUrl(url);
            log.info(tsdm.getAnimeName()+" "+title+"传送门："+url);
            bduDao.updateTsdm(tsdm);
            put(tsdm.getAnimeName(),title,"youku",url);
        }

    }

    public void parseBiliId(){
        Tsdm tsdm=new Tsdm();
        tsdm.setCopyright("bili");
        List<Tsdm> tsdms=bduDao.selectTsdm(tsdm);
        for(Tsdm t:tsdms){
            List<Bangumi> bangumis;
            String name=t.getAnimeName();
            do{
                bangumis=biliDao.selectBangumi(new Bangumi(null,null,name));
                if(bangumis.size()!=1){
                    log.info(bangumis);
                    log.info("无法准确从bili番剧表找到这部番剧："+name+"请输入别名");
                    Scanner sc=new Scanner(System.in);
                    name=sc.nextLine();
                }else{
                    break;
                }
            }while (true);
            t.setBiliId(bangumis.get(0).getSeasonId()+"");
            Document document=CrawlerUtil.jsoupGet(ApiUrl.bangumiAnime.s(2).getUrl(t.getBiliId()),CrawlerUtil.DataType.domcument, Connection.Method.GET);
            String time=document.select(".info-update>em>span").last().text();
            t.setUpdateTime(time);
            bduDao.updateTsdm(t);
        }
    }

    public void parseBilibili(Tsdm tsdm) {
        if(tsdm.getBiliId()==null){
            log.info(tsdm.getAnimeName()+"\tbiliId没有入库无法获取番剧更新信息");
            return;
        }
        JsonNode jsonNode = CrawlerUtil.jsoupGet(ApiUrl.bangumiAnime.getUrl(tsdm.getBiliId()), CrawlerUtil.DataType.json, Connection.Method.GET);
        if(jsonNode==null||jsonNode.get("result")==null){
            log.error(tsdm.getAnimeName()+"无法获取专题信息");
        }
        jsonNode = jsonNode.get("result").get("episodes");
        if (jsonNode.size() > 0) {
            jsonNode = jsonNode.get(0);
            String av = jsonNode.get("av_id").asText();
            String index_title = jsonNode.get("index_title").asText();
            String index = jsonNode.get("index").asText();
            String url=ApiUrl.AV.getUrl(av, 1);
            if(StringUtils.isEmpty(tsdm.getBiliUrl())||!tsdm.getBiliUrl().equals(url)) {
                tsdm.setBiliUrl(url);
                bduDao.updateTsdm(tsdm);
                String msg = tsdm.getAnimeName() + "\t已更新第" + index + "集\t" + index_title + "\n传送门:" + url;
                log.info(msg);
                put(tsdm.getAnimeName(), index_title, "Bilibili", url);
            }
        } else {
            log.info(tsdm.getAnimeName() + "\t没开播");
        }

    }

    public void parseiqiyiId(){
        Tsdm tsdm=new Tsdm();
        tsdm.setCopyright("爱奇艺");
        List<Tsdm> tsdms=bduDao.selectTsdm(tsdm);
        Document document=CrawlerUtil.jsoupGet(ApiUrl.iqiyiBangumi.getUrl(),CrawlerUtil.DataType.domcument, Connection.Method.GET);
        Elements elements=document.select("#widget-tab-0 > div.o-hidden .site-piclist_info_title>a");
        for(Tsdm t:tsdms){
            List<Bangumi> bangumis;
            String name=t.getAnimeName();
            Elements as=null;
            do{
                as=elements.select(":contains("+name+")");
                if(as.size()!=1){
                    log.info(as);
                    log.info("无法准确从爱奇艺新番放松表找到这部番剧："+name+"请输入别名");
                    Scanner sc=new Scanner(System.in);
                    name=sc.next();
                }else{
                    break;
                }

            }while (true);
            document=CrawlerUtil.jsoupGet(as.attr("href"),CrawlerUtil.DataType.domcument,Connection.Method.GET);
            String href=document.select("a.c999").last().attr("href");
            String iqiyiID=href.substring(href.lastIndexOf("_")+1,href.lastIndexOf("."));
            t.setIqiyiId(iqiyiID);
            bduDao.updateTsdm(t);
        }
    }

    public void parseiqiyi(Tsdm tsdm) {
        if(tsdm.getIqiyiId()==null){
            log.info(tsdm.getAnimeName()+"\tiqiyiID没有入库无法获取番剧更新信息");
            return;
        }
        Document document=CrawlerUtil.jsoupGet(ApiUrl.iqiyiPlay.getUrl(tsdm.getIqiyiId()),CrawlerUtil.DataType.domcument, Connection.Method.GET);
        Element as=document.select(".wrapper-piclist li:not(:contains(预告)) a").last();
        String href=as.attr("href");
        String title=as.text();

        if(StringUtils.isEmpty(tsdm.getIqiyiUrl())||!tsdm.getIqiyiUrl().equals(href)) {
            tsdm.setIqiyiUrl(href);
            bduDao.updateTsdm(tsdm);
            String msg = title + "\t已更新，播放地址：" + href;
            log.info(msg);
            put(tsdm.getAnimeName(), title, "iqiyi", href);
        }
    }

    @Scheduled(cron = "0 0/10 * * * ?")
    public void parseTsdm() {
        Tsdm t=new Tsdm();
//        t.setAnimeName("末日时在做什么？有没有空？可以来拯救吗？");
        t.setTsdmUrl("");
        List<Tsdm> tsdms=bduDao.selectTsdm(t);
//        boolean f=true;
        for(Tsdm tsdm:tsdms) {
//            if(tsdm.getAnimeName().equals("进击的巨人 season 2")){
//                f=false;
//            }
//            if(f){
//                continue;
//            }
            log.info(tsdm);
            String url = tsdm.getTsdmUrl();
            Document postmessage = CrawlerUtil.jsoupGet(url, CrawlerUtil.DataType.domcument, Connection.Method.GET);
            //
            Elements pstatus = postmessage.select(".pstatus");

            JsonNode jsonNode=null;
            try {
                jsonNode=new ObjectMapper().readTree(tsdm.getLastUpdateTimes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < pstatus.size(); i++) {
                Element a = pstatus.get(i);
                Element td = a.parent();

                Elements panHref = td.select("a[href*=pan.baidu.com]");
                if (panHref.size() == 0) {
                    continue;
                }


                String lastUpdate[] = a.text().split(" ");
                log.debug(Arrays.asList(lastUpdate));

                tsdm.setIndex(i);
                tsdm.setLastUpdateTimes(lastUpdate[3] + " " + lastUpdate[4]);
                bduDao.updateTsdm(tsdm);
                if(jsonNode.get(i+"")!=null){
                    String format="yyyy-MM-dd HH:mm";
                    Date result = TimeUtil.parseDate(jsonNode.get(i+"").asText(), format);
                    Date now = TimeUtil.parseDate(lastUpdate[3] + " " + lastUpdate[4], format);
                    if(result.getTime()<now.getTime()){
                        String num = a.parent().attr("id").replaceAll("\\D", "");
                        log.info("天使论坛已经更新该番剧"+tsdm.getAnimeName()+"资源，传送门："+tsdm.getTsdmUrl()+"#"+num);
//                        DoSoming.messagePush(msg);
                    }else{
                        continue;
                    }
                }else{
//                    continue;
                }

                //具体资源链接。

                //获取字幕组信息
                Elements subs = td.select("strong:matches([\\[\\【]|1280X720|1920X1080|MP4|MKV|GB|BIG5|720P|1080P)");

                //排除不是字幕组的信息
                Elements strikes=td.select("strong:has(strike)");
                subs.removeAll(strikes);
                List<TextNode> text=new ArrayList<>();
                if(subs.size()==0){
                    for(TextNode textNode:td.textNodes()){
                        if(textNode.text().contains("[")||textNode.text().contains("【")){
                            text.add(textNode);
                        }
                    }
                }
                boolean flag=subs.size()>0;
                List list=flag?subs:text;
                log.info("捕捉到的字幕组信息：\n"+list);
                int index = 0;
                List<Node> children = td.childNodes();
                //遍历楼层里面的所有百度云链接
                for (Element pan : panHref) {
                    String subtitle=null;
                    if(list.size() > 0 ) {
                        boolean isLast = index==list.size()-1;
                        Element p=pan;
                        Node c;
                        c=(Node) list.get(isLast?index:index+1);
                        while (!children.contains(c)){
                            c=c.parent();
                        }
                        while (!children.contains(p)){
                            p=pan.parent();
                        }

                        if (list.size() == 1 && flag) {
                            subtitle = ((Elements) list).first().text();
                        } else if (list.size() > 0 && list.size() == 1 && !flag) {
                            subtitle = ((TextNode) list.get(0)).text();
                        } else {
                            //同一个楼层有多个字幕组资源情况下，需要判断当前资源所属字幕组
                            if (isLast && flag) {
                                subtitle = ((Elements) list).last().text();
                            } else if (isLast && !flag) {
                                subtitle = ((TextNode) list.get(list.size() - 1)).text();
                            } else if (c != null && children.indexOf(p) > children.indexOf(c)) {
                                index++;
                            }
                            if (!isLast) {
                                if (flag) {
                                    subtitle = ((Element) list.get(index)).text();
                                } else {
                                    subtitle = ((TextNode) list.get(index)).text();
                                }
                            }
                        }
                    }else{
                        log.error("找不到字幕组信息");
                    }

                    String episode=null;
                    Element element = pan.previousElementSibling();
                    Node node = pan.previousSibling();

                    if (element != null && !element.text().isEmpty()) {
                        String d = element.text().replaceAll("[:：]", "");
                        if (!d.isEmpty()) {
                            episode = d;
                        }
                    }
                    String remark=null;
                    if (node != null && node instanceof TextNode&&!((TextNode) node).isBlank() && !node.equals(element)) {
                        remark = ((TextNode) node).text().replaceAll("[:：\\.\\s链鏈接]", "");
                        if (remark.isEmpty()) {
                            remark=null;
                        }
                    }

                    String href = pan.attr("href");
                    String password = null;



                    if(pan.nextElementSibling()!=null&&"font".equals(pan.nextElementSibling().tagName())){
                        password=pan.nextElementSibling().text();
                    }else{
                        Node n=pan.nextSibling();
                        while (n instanceof Element){
                            n= n.nextSibling();
                        }
                        password=((TextNode)n).text();
                    }
                    password = StringUtil.matchStr(password,"\\w{4}",String.class);

                    if(password==null){
                        log.error("没有密码");
                    }

                    Bdu bdu=new Bdu(href,password,subtitle,episode,remark,tsdm.getAnimeName());
                    log.info(bdu);

                    try {
                        bduDao.insertBdu(bdu);
                        DoSoming.messagePush(tsdm.getAnimeName() + "资源已更新：" + bdu);
                    } catch (DuplicateKeyException e) {
                        bduDao.updateBdu(bdu);
                    }
                }

            }


        }
    }


    public void tsdm() {

        //获取萌娘百科番剧条目
        Document anime = CrawlerUtil.jsoupGet("https://zh.moegirl.org/zh-hans/日本2017年春季动画", CrawlerUtil.DataType.domcument, Connection.Method.GET);
        //萌娘百科番局条目父元素
        Element mw = anime.select("#mw-content-text").first();

        for (Element a : anime.select("li.toclevel-1:gt(0)>a")) {
            Element span = mw.select("[id=" + a.attr("href").replace("#", "") + "]").first();
            String title = span.text();
            if (title.equals("参见")) {
                break;
            }
            log.info("title=" + title);
            Element dl = span.parent().nextElementSibling();
            int i = 1;
            while (!dl.tagName().equals("dl")) {
                dl = dl.nextElementSibling();
                i++;
            }

            String time[] = dl.select("dd:eq(1)").text().split(" ");
            log.info("时间：" + Arrays.asList(time));
            //格式化开播时间
            String playtime = time[0].replace("起", "");
            Date formatPlayTime = DateUtils.parseDate(playtime, new String[]{"yyyy年MM月dd日", "yyyy年MM月"});

            String updatetime = time[1];
            String copyright = "";
            //如果中国大陆有放送权提取放松地址信息
            if (dl.text().contains("中国大陆")) {
                copyright = dl.select("dd:contains(中国大陆)").first().text().split("：")[1];
                log.info("中国大陆放送权：" + copyright);
            }
            Tsdm tsdm = new Tsdm(title, formatPlayTime, updatetime, copyright);

            try {
                bduDao.insertTsdm(tsdm);
            } catch (DuplicateKeyException e) {
                log.error("异常信息" + e.getMessage());
            }

        }
    }

    @Scheduled(cron = "0 0/10 * * * ?")
    public void check() {
        if(todayTsdms==null){
            doUpdate();
        }
        for (Tsdm tsdm : todayTsdms) {
            String copyrights[] = tsdm.getCopyright().split("／");
            log.info(tsdm.getAnimeName() + "版权:" + Arrays.asList(copyrights));
            if (copyrights != null) {
                List<String> strings = Arrays.asList(copyrights);
                if (strings.contains("Bilibili")) {
                    parseBilibili(tsdm);
                } else if (strings.contains("优酷网") || strings.contains("土豆网")) {
                    parseYouku(tsdm);
                } else if (strings.contains("爱奇艺")) {
                    parseiqiyi(tsdm);
                }

            }
        }

    }


    @Scheduled(cron = "0 0 0/24 * * ?")
    public void doUpdate() {
        Date date = new Date();
        String e = TimeUtil.formatDate(date, "E").replace("星期", "每周");
        String hour = TimeUtil.formatDate(date, "HH:mm");
        Tsdm t=new Tsdm();
        t.setUpdateTime("%" +e + "%");
        todayTsdms = bduDao.selectTsdm(t);
        boolean flag = "00:00".equals(hour);
        if (flag) {
            pushUrl = new HashMap<>();
        }
        List<String> strings = new ArrayList<>();
        for (Tsdm tsdm : todayTsdms) {
            if (flag) {
                pushUrl.put(tsdm.getAnimeName(), new HashMap<>());
            }
            strings.add(tsdm.toString());
        }
        log.info(strings);
        DoSoming.messagePush(strings);
    }



    public static void main(String[] args) {
        DynamicTimer dynamicTimer=new DynamicTimer(()->{
            log.info(123);
        },"0/1 * * * * ?");
        int i=10;
        while (i-->0){
            ThreadUtil.sleep(null,1);
        }
    }

}