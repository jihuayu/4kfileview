<!DOCTYPE html>

<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, user-scalable=yes, initial-scale=1.0">
    <title>${file.name}3D预览</title>
	  <script src="js/base64.min.js" type="text/javascript"></script>
    <#include "*/commonHeader.ftl">
	
</head>
	<#if currentUrl?contains("http://") || currentUrl?contains("https://") || currentUrl?contains("file://")>
    <#assign finalUrl="${currentUrl}">
  <#elseif currentUrl?contains("ftp://") >
   <#assign finalUrl="${currentUrl}">
<#else>
    <#assign finalUrl="${baseUrl}${currentUrl}">
</#if>
<body>
<iframe src="" width="100%" frameborder="0"></iframe>
</body>
<script type="text/javascript">
    var url = '${finalUrl}';
    var kkagent = '${kkagent}';
    var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
    var modelUrls = [{url: url, name: '${file.name?js_string}'}];
<#if online3DResourceUrls??>
<#list online3DResourceUrls as resourceUrl>
    modelUrls.push({url: '${resourceUrl?js_string}', name: fileNameFromUrl('${resourceUrl?js_string}')});
</#list>
</#if>

    function fileNameFromUrl(sourceUrl) {
        try {
            var parsedUrl = new URL(sourceUrl, window.location.href);
            var path = parsedUrl.pathname;
            return decodeURIComponent(path.substring(path.lastIndexOf('/') + 1)) || 'resource';
        } catch (e) {
            var cleanUrl = sourceUrl.split('?')[0];
            return decodeURIComponent(cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1)) || 'resource';
        }
    }

    function toViewerUrl(source) {
        var viewerUrl = source.url;
        if (kkagent === 'true' || !viewerUrl.startsWith(baseUrl)) {
            viewerUrl = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(viewerUrl)) + "&key=${kkkey}";
            viewerUrl += "&fullfilename=/" + encodeURIComponent(source.name);
        }
        return viewerUrl;
    }

    document.getElementsByTagName('iframe')[0].src = "${baseUrl}website/index.html#model=" + modelUrls.map(toViewerUrl).join(",");
	
    document.getElementsByTagName('iframe')[0].height = document.documentElement.clientHeight - 10;
    /**
     * 页面变化调整高度
     */
    window.onresize = function () {
        var fm = document.getElementsByTagName("iframe")[0];
        fm.height = window.document.documentElement.clientHeight - 10;
    }
</script>

  <script type="text/javascript">
   		 /*初始化水印*/
 if (!!window.ActiveXObject || "ActiveXObject" in window)
{
}else{
 initWaterMark();
}
</script>
</html>
