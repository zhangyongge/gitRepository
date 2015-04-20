#encoding=utf-8
import urllib2

mmurl = 'http://mm.taobao.com/json/request_top_list.htm?type=0&page='
i = 0
while i < 1:
	url = mmurl + str(i)
	i += 1
	up = urllib2.urlopen(url)
	content = up.read()
	
	ahref = '<a href="'
	target = 'target'
	start = content.find(ahref)
	end = content.find(target)	
	
	modelurl = content[start + len(ahref) : end]
	print modelurl
	client = urllib2.urlopen(modelurl)
	modelContent = client.read()
	imgHead = '<img style'
	imgTail = '.jpg'
	imgStart = modelContent.find(imgHead)
	imgEnd = modelContent.find(imgTail, imgStart)

	contTemp = modelContent[imgStart : imgEnd + len(imgTail)]
	
		
	imgStart = contTemp.find('src="')
	#print modelContent[imgStart + 5 : imgEnd + len(imgTail)]
