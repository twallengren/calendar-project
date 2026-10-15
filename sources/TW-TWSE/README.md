# TW-TWSE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `twse-holiday-schedules` | TWSE 有價證券集中交易市場開（休）市日期表 / Holiday Schedule, 2010-2026 | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/zh/trading/holiday.html | 2026-10-14 | 2010-01-01 to 2026-12-31 | The exchange's annual table of non-trading days: 2010-2020 from archived captures of its trading_days / holidaySchedule pages (zh; the 2017 English table listing the 'Additional Trading Days on Saturday'), 2021-2026 from its live schedule endpoint (rwd/zh/holidaySchedule/holidaySchedule?date=<year>0101). Lists every holiday, substitute and bridge day, the settlement-only days before Lunar New Year, Labour Day, and the make-up Saturdays with whether the market trades (補行交易 through 2018; 補行上班，但不交易亦不交割 from 2019). The 2027 table was not yet published (the endpoint returned no rows). Retrieved via the Internet Archive captures recorded in sources/TW-TWSE/manifest.csv. |
| `twse-trading-days` | TWSE 每日市場成交資訊 (daily market summary), every month January 2010 to October 2026, and the trading-day table compiled from it | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/zh/trading/historical/fmtqik.html | 2026-10-14 | 2010-01-04 to 2026-10-14 | The exchange's daily market summary (rwd/zh/afterTrading/FMTQIK?date=<year><month>01) lists every day the centralised market traded; the 202 monthly responses are preserved and twse-trading-days.csv compiles their 4,112 dates. The generated calendar's business days equal this record exactly, day by day, including the fifteen Saturday sessions and excluding every typhoon closure and settlement-only day. Retrieved via the Internet Archive captures recorded in sources/TW-TWSE/manifest.csv. |
| `twse-factbook` | TWSE Fact Book, 'Stock Trading Volume and Value: No. of Trading Days' (2015, 2020 and 2025 editions) | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/zh/about/company/factbooks.html | 2026-10-14 | 2010-01-01 to 2024-12-31 | Annual trading-day counts 2010-2024 (251, 247, 250, 246, 248, 244, 244, 246, 247, 242, 245, 244, 246, 239, 242) and monthly counts for 2014, 2019 and 2024, all equal to the daily record and to the modelled calendar. |
| `twse-suspended` | TWSE 天然災害停止上班之處理 / Market closure for natural disasters (rule page, zh and en) and the related FAQ | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/zh/clearing/suspended.html | 2026-10-14 | current rule | "台北市市長宣布當日或當日上午台北市全體公教機關停止上班時，本公司集中交易市場全日休市": a full-day or morning suspension in Taipei closes the market for the whole day; an afternoon-only suspension keeps it open and cancels post-close trading; settlement due that day is postponed. |
| `twse-history` | TWSE corporate history and timeline (history.html; 60-year history, section 225) | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/en/about/company/history.html | 2026-10-14 | 1993-2026 | Session 09:00-13:30 since 2 January 2001; "2019, Jan 1: The TWSE forgoes Saturday trading and settlement, even with the presence of work"; the 1993 typhoon-closure rule and the 1995 election-day closure rule; continuous matching from 23 March 2020. |
| `twse-trading-system` | TWSE 集中市場交易制度介紹 (trading system and session times) | Taiwan Stock Exchange Corporation | https://www.twse.com.tw/zh/products/system/trading.html | 2026-10-14 | current sessions | Regular session 09:00-13:30, after-hours fixed-price 14:00-14:30, odd-lot sessions; no shortened sessions. |
| `twse-eshop-lny-2026` | Market Closure for the 2026 Lunar New Year Holiday (TWSE eshop notice, 12 February 2026) | Taiwan Stock Exchange Corporation | https://eshop.twse.com.tw/en/news/detail/8a82e9e69c3aecea019c51422d670027 | 2026-10-14 | 2026-02-12 to 2026-02-20 | The exchange's English notice that the market is closed from 12 to 20 February 2026 and resumes on 23 February. |
| `law-moj-D0020033` | 紀念日及節日實施辦法 (Regulations on Commemorative Days and Festivals; abolished 6 June 2025) | Ministry of the Interior, Taiwan (law.moj.gov.tw) | https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=D0020033 | 2026-10-14 | 2010-01-01 to 2025-06-05 | The holidays and, from 1 January 2015 (第5條之1), the substitute rule: a Saturday holiday is made up on the preceding working day and a Sunday one on the next, the Lunar New Year block always on the next. |
| `law-moj-D0020095` | 紀念日及節日實施條例 (Act on Commemorative Days and Festivals, promulgated 28 May 2025) | Ministry of the Interior, Taiwan (law.moj.gov.tw) | https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=D0020095 | 2026-10-14 | 2025-05-28 onward | The five-day Lunar New Year block from the day before the Eve, and the new days off: Labour Day for all, Teachers' Day, Retrocession Day, Constitution Day; weekend occurrences made up. |
| `dgpa-calendars` | 政府行政機關辦公日曆表 (government office calendars) 2017-2027, Directorate-General of Personnel Administration | Directorate-General of Personnel Administration, Executive Yuan, Taiwan | https://data.gov.tw/dataset/14718 | 2026-10-14 | 2017-01-01 to 2027-12-31 | Daily flags for holidays, substitute days, bridge days and make-up working Saturdays. The 2027 calendar supplies the projected 2027 closures; 2026 and 2027 contain no make-up Saturdays. |
| `taipei-suspension-register` | 臺北市歷次天然災害停止上班上課訊息 (Taipei City Government register of natural-disaster work and school suspensions) | Taipei City Government (data.taipei) | https://data.gov.tw/dataset/132177 | 2026-10-14 | 2010-01-01 to 2026-10-14 | Every suspension decision since 2010 with its scope (full day, morning, afternoon, work and/or school). Each typhoon closure in this calendar is a full-day suspension here; the afternoon-only suspensions of 12 June 2012, 12 July 2013 and 10 July 2018 did not close the market. |
| `cbc-2019-09-30` | Central Bank of the Republic of China release 108新聞發布第163號 (30 September 2019): foreign-exchange market closed for Typhoon Mitag | Central Bank of the Republic of China (Taiwan) | https://www.cbc.gov.tw/tw/cp-302-104498-02adc-1.html | 2026-10-14 | 2019-09-30 | Corroborates the 30 September 2019 closure. |
| `press-amcham-2019-10` | AmCham Taiwan Business TOPICS: Goodbye Saturday Securities Trading | Press (quoting the exchange) | https://topics.amcham.com.tw/2019/10/securities-trading/ | 2026-10-14 | 2019 | AmCham Taiwan Business TOPICS, October 2019: "Goodbye Saturday Securities Trading". |
| `press-businesstoday-2024-07-23` | 今周刊 颱風假放了台股是否開盤？證交所透露休市關鍵 | Press (quoting the exchange) | https://www.businesstoday.com.tw/article/category/183027/post/202407230027/ | 2026-10-14 | 2024-07-24 | 今周刊 23 July 2024: the exchange's typhoon rule and the 24 July closure. |
| `press-bloomberg-2015-09-28` | Carrier Management/Bloomberg: Typhoon Dujuan closures | Press (quoting the exchange) | https://carriermanagement.com/news/2015/09/28/145757.htm | 2026-10-14 | 2015-09-29 | Bloomberg 28 September 2015: "The stock markets will be closed for all of Tuesday" (Typhoon Dujuan). |
| `press-cmoney-2016-07-08` | CMoney note: 尼伯特 8日 股匯市休市 | Press (quoting the exchange) | https://www.cmoney.tw/notes/note-detail.aspx?nid=93837 | 2026-10-14 | 2016-07-08 | CMoney 8 July 2016: "股匯市也休市" (Typhoon Nepartak); the exchange's own statement was not located, the closure is in its daily record. |
| `press-cnyes-2024-07-23` | 鉅亨網 北北基明停班停課 台北股匯期休市一天 (凱米) | Press (quoting the exchange) | https://news.cnyes.com/news/id/5648420 | 2026-10-14 | 2024-07-24 | 鉅亨網 23 July 2024: "證券交易所今 (23) 日晚間表示…明 (24) 日集中交易市場全日休市" (Typhoon Gaemi). |
| `press-cnyes-2024-10-01` | 鉅亨網 強颱山陀兒來襲！台北股匯10/2休市一天 | Press (quoting the exchange) | https://news.cnyes.com/news/id/5729077 | 2026-10-14 | 2024-10-02 | 鉅亨網: 台北股匯 2 October 2024 closed (Typhoon Krathon). |
| `press-ctee-2012-08-02` | 工商時報 休市 除權息、款券交割順延 (蘇拉) | Press (quoting the exchange) | https://www.ctee.com.tw/news/20120802700042-439901 | 2026-10-14 | 2012-08-02 | 工商時報 2 August 2012: "台北市政府宣布今（2）日停止上班，股市也配合休市，應屆交割款、券…順延" (Typhoon Saola). |
| `press-ctee-2013-08-21` | 工商時報 潭美來襲 股匯今休市 | Press (quoting the exchange) | https://www.ctee.com.tw/news/20130821700183-439901 | 2026-10-14 | 2013-08-21 | 工商時報 21 August 2013: "證交所表示，因股市休市，原應辦交割事宜則順延至次一營業日" (Typhoon Trami). |
| `press-ctee-2019-08-08` | 工商時報 颱風來襲 台股明日休市一天 (利奇馬) | Press (quoting the exchange) | https://www.ctee.com.tw/news/20190808700734-439803 | 2026-10-14 | 2019-08-09 | 工商時報 8 August 2019: "台灣證券交易所表示…8月9日集中交易市場全日休市" (Typhoon Lekima). |
| `press-epochtimes-2012-06-12` | 大紀元 台北市政府宣布今天下午停班停課 | Press | https://www.epochtimes.com/gb/news391_29920.htm | 2026-10-14 |  | 大紀元 台北市政府宣布今天下午停班停課 |
| `press-ettoday-2014-07-22` | ETtoday 麥德姆颱風來襲 台股明日休市 | Press (quoting the exchange) | https://finance.ettoday.net/news/381304 | 2026-10-14 | 2014-07-23 | ETtoday 22 July 2014: "證交所及櫃買中心宣布，明(23)日台股全日休市" (Typhoon Matmo). |
| `press-ettoday-2016-09-28` | ETtoday 梅姬掃台停電 (9/28 股匯市休市) | Press (quoting the exchange) | https://finance.ettoday.net/news/783539 | 2026-10-14 | 2016-09-28 | ETtoday 28 September 2016: "全國22個縣市28日全數停班停課…台北股匯市因此休市一天" (Typhoon Megi, second day). |
| `press-ettoday-2019-02-15` | ETtoday 證交所：228連假證券市場休市 23日補班不交易也不交割 | Press (quoting the exchange) | https://finance.ettoday.net/news/1378428 | 2026-10-14 | 2019-02-23 to 2019-03-01 | ETtoday 15 February 2019 quoting the exchange: closed 28 February-1 March 2019; 23 February a make-up working day "但不交易也不交割". |
| `press-ettoday-2019-09-29` | ETtoday 米塔颱風來襲北市停班停課 股匯市30日休市一天 | Press (quoting the exchange) | https://finance.ettoday.net/news/1546086 | 2026-10-14 | 2019-09-30 | ETtoday 29 September 2019: the exchange's rule and the 30 September closure (Typhoon Mitag). |
| `press-ettoday-2023-08-02` | ETtoday 颱風影響股匯市！明休市1天 (卡努) | Press (quoting the exchange) | https://finance.ettoday.net/news/2553010 | 2026-10-14 | 2023-08-03 | ETtoday 2 August 2023: "台灣證券交易所晚間表示…明（8月3日）集中交易市場全日休市" (Typhoon Khanun). |
| `press-ettoday-2025-10-29` | ETtoday 明年農曆年台股休市11天 (2026 calendar) | Press (quoting the exchange) | https://finance.ettoday.net/news/3058648 | 2026-10-14 | 2026 | ETtoday 29 October 2025: the exchange announces its 2026 calendar. |
| `press-ettoday-2026-07-09` | ETtoday 巴威颱風來襲 台北股匯市明日休市 | Press (quoting the exchange) | https://finance.ettoday.net/news/3198381 | 2026-10-14 | 2026-07-10 | ETtoday 9 July 2026 quoting the exchange: "7月10日集中交易市場全日休市…當日應屆交割款券順延辦理" (Typhoon Bavi). |
| `press-mondovisione-2008-12` | Mondo Visione: TWSE announces three working Saturdays in 2009 | Press (quoting the exchange) | https://mondovisione.com/media-and-resources/news/taiwan-stock-exchange-corporation-announces-three-working-saturdays-in-2009-2011124/ | 2026-10-14 | 2009 | Mondo Visione, December 2008: the exchange's notice of three Saturday trading sessions in 2009 (context for the pre-2019 practice). |
| `press-newtalk-2024-10-03` | Newtalk 山陀兒 10/3 續休市 (證交所公告) | Press (quoting the exchange) | https://newtalk.tw/news/view/2024-10-03/938679 | 2026-10-14 | 2024-10-03 | Newtalk 3 October 2024 quoting the exchange: "113年10月3日集中交易市場全日休市，當日應屆交割款券順延辦理" (Typhoon Krathon, second day). |
| `press-nownews-undated-2012-06-12-event` | NOWnews 回顧 2012-06-12 台北市停班停課宣布時間 | Press | https://www.nownews.com/news/6744726 | 2026-10-14 |  | NOWnews 回顧 2012-06-12 台北市停班停課宣布時間 |
| `press-pts-2018-06-08` | 公視 週六補班台股交易 (2017 volumes) | Press (quoting the exchange) | https://news.pts.org.tw/article/396371 | 2026-10-14 | 2018 | PTS 8 June 2018: consultation on ending Saturday trading; 2017 Saturday turnover 44-59% of the daily average. |
| `press-taiwannews-2022-01-07` | Taiwan News (zh) 證交所: 1/31-2/4 休市, 1/22 補班不交易不交割 | Press (quoting the exchange) | https://www.taiwannews.com.tw/zh/news/4402107 | 2026-10-14 | 2022-01-22 to 2022-02-04 | Taiwan News 7 January 2022: the exchange closed 27 January-4 February 2022; 22 January make-up day without trading. |
| `press-taiwannews-2024-10-31` | Taiwan News: Taiwan stock market closed Oct. 31 (Kong-rey) | Press (quoting the exchange) | https://www.taiwannews.com.tw/en/news/5962323 | 2026-10-14 | 2024-10-31 | Taiwan News / CNA 31 October 2024: market closed, settlements postponed (Typhoon Kong-rey). |
| `press-technews-2018-07-03` | TechNews/中央社 明年週六補班台股將不交易 | Press (quoting the exchange) | https://finance.technews.tw/2018/07/03/working-sat-stop-stock-market-in-2019/ | 2026-10-14 | 2019-01-01 onward | CNA/TechNews 3 July 2018: the FSC chairman announces no trading on the 2019 make-up Saturdays ("明年週六補班台股將不交易"). |
| `press-tvbs-2012-06-12` | TVBS 豪雨狂降 12日停班停課一覽表 | Press | https://news.tvbs.com.tw/life/20878 | 2026-10-14 |  | TVBS 豪雨狂降 12日停班停課一覽表 |
| `press-udn-2025-10-29` | 經濟日報 證交所行事曆出爐 明年春節股市連休11天 | Press (quoting the exchange) | https://money.udn.com/money/story/5607/9105340 | 2026-10-14 | 2026 | 經濟日報 29 October 2025: the exchange's 2026 calendar. |
| `press-yahoo-2024-07-24` | Yahoo股市 台股再休市1天！中鋼除息順延 (凱米 7/25) | Press (quoting the exchange) | https://tw.stock.yahoo.com/news/%E5%8F%B0%E8%82%A1%E5%86%8D%E4%BC%91%E5%B8%821%E5%A4%A9%EF%BC%81%E4%B8%AD%E9%8B%BC%E9%99%A4%E6%81%AF%E9%A0%86%E5%BB%B6-%E4%B8%80%E8%A1%A8%E7%9C%8B%E5%87%B1%E7%B1%B3%E5%BD%B1%E9%9F%BF80%E6%AA%94%E6%A8%99%E7%9A%84-120155629.html | 2026-10-14 | 2024-07-25 | Yahoo/鉅亨 24 July 2024: "明天集中交易市場再休市1天" (Typhoon Gaemi, second day). |
| `press-cna-2012-12-25` | 中央商情網 via yam 國際金融市場日誌 (2013 TWSE calendar) | Press (quoting the exchange) | https://n.yam.com/Article/20121225085975 | 2026-10-14 | 2013 | 中央商情網 25 December 2012: the exchange's 2013 calendar. |
| `press-cna-2015-07-09` | 中央社 via yam 10日股匯市休市一天 (昌鴻) | Press (quoting the exchange) | https://n.yam.com/Article/20150709145457 | 2026-10-14 | 2015-07-10 | CNA 9 July 2015 (yam): "104年7月10日股票交易市場全日休市，當日應屆交割款券順延辦理" (Typhoon Chan-hom). |

## Modelling decisions recorded against these sources

- **The exchange's annual table is the authority, and its daily record proves it.** The TWSE
  publishes an 開（休）市日期表 for every year (2010-2020 from archived captures of its site,
  2021-2026 from its live schedule endpoint, `twse-holiday-schedules`), and its daily market
  summary (每日市場成交資訊, `twse-trading-days`) lists every day the centralised market traded.
  The generated calendar's business days equal that record exactly for every day from 4 January
  2010 to the retrieval date, and the annual counts equal the Fact Book's "No. of Trading Days"
  (`twse-factbook`). Every closure is therefore an explicit date; nothing shifts by rule.
- **Taiwan's observance rules changed twice** (`law-moj-D0020033`, `law-moj-D0020095`): no
  substitute for a weekend holiday before 2015 (except the Lunar New Year block); from 2015 a
  Saturday holiday is made up on the preceding working day and a Sunday one on the next; from
  the 2025 Act the Lunar New Year block starts a day earlier and Teachers' Day, Retrocession Day
  and Constitution Day are days off. Bridge days (調整放假) attach a Monday or Friday to a Tuesday
  or Thursday holiday and are made up on a Saturday. The modules name each substitute and bridge
  day.
- **The exchange closes two business days before each Lunar New Year holiday** for clearing
  and settlement only ("市場無交易，僅辦理結算交割作業"); those days are non-trading days here
  (`tw_twse_lunar_new_year_settlement_days`). Labour Day closed the exchange every year before
  it became a statutory holiday in 2025 because the exchange follows the banking industry's
  holiday ("銀行業於5月2日（星期一）補假1日，爰本公司市場當日補假1日", 2011 table).
- **Make-up Saturdays were trading days until 2018.** The exchange held a full session on each
  government make-up working Saturday of 2010-2018 (fifteen sessions, each listed in its table
  and present in its daily record; "Additional Trading Days on Saturday: February 18, June 3,
  September 30" in the 2017 English table) and "forgoes Saturday trading and settlement, even
  with the presence of work" from 1 January 2019 (`twse-history`; every later table says
  "補行上班，但不交易亦不交割"). `modules/policies/tw_twse_weekends.yaml` expresses each session
  as a one-day weekend period in which only Sunday is a weekend day. No election day (all
  Saturdays) coincided with a session.
- **Typhoon closures.** The exchange closes for the whole day when the Taipei City Government
  suspends work for the day or the morning, stays open for an afternoon-only suspension (and
  cancels post-close trading), and postpones settlement (`twse-suspended`). The seventeen
  full-day closures 2012-2026 are each a full-day suspension in the city's register
  (`taipei-suspension-register`), absent from the exchange's daily record and announced by the
  exchange (press quoting it). The afternoon-only suspensions of 12 June 2012, 12 July 2013 and
  10 July 2018 left the session intact. No system outage or other unscheduled closure exists in
  the daily record.
- **No early closes.** The session has been 09:00-13:30 since 2 January 2001 (`twse-history`);
  none of the seventeen annual tables lists a shortened day, and the exchange's disaster rule
  knows only full-day closure or post-close cancellation.
- **2027 is projected.** The exchange's 2027 table was not yet published at retrieval; the 2027
  rows follow the government's 116年 calendar (`dgpa-calendars`) with the two settlement-only days
  projected from the unbroken 2010-2026 pattern, and the year is `PROJECTED`.
- **`coverage.verified_through`: 2026-12-31**, the last year with an exchange table.
