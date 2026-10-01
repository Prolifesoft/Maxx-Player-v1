import urllib.request
import json
from datetime import datetime, timedelta

api_key = "15745c1c46c264d1170db38ea66049e0"
today = datetime.now()
in_30_days = today + timedelta(days=30)
in_60_days = today + timedelta(days=60)

date_today = today.strftime('%Y-%m-%d')
date_30 = in_30_days.strftime('%Y-%m-%d')
date_60 = in_60_days.strftime('%Y-%m-%d')

url_30 = f"https://api.themoviedb.org/3/discover/movie?api_key={api_key}&language=tr-TR&primary_release_date.gte={date_today}&primary_release_date.lte={date_30}&sort_by=popularity.desc"
res = urllib.request.urlopen(url_30).read()
print(json.loads(res)['results'][0]['title'])

url_60 = f"https://api.themoviedb.org/3/discover/movie?api_key={api_key}&language=tr-TR&primary_release_date.gte={date_today}&primary_release_date.lte={date_60}&sort_by=popularity.desc"
res = urllib.request.urlopen(url_60).read()
print(json.loads(res)['results'][0]['title'])
