package scalaj.http

import java.net.URL

case class Token(key: String, secret: String)

object OAuth {
  import java.net.URI
  import javax.crypto.Mac
  import javax.crypto.SecretKey
  import javax.crypto.spec.SecretKeySpec
  val MAC = "HmacSHA1"
      
  def sign(req: Http.Request, consumer: Token, token: Option[Token], verifier: Option[String]): Http.Request = {

    
    val baseParams:List[(String,String)] = List(
      ("oauth_timestamp", (System.currentTimeMillis/1000).toString),
      ("oauth_nonce", System.currentTimeMillis.toString)
    )
    
    var (oauthParams, signature) = getSig(baseParams, req, consumer, token, verifier)
    
    oauthParams ::= ("oauth_signature", signature)
    
    req.header("Authorization", "OAuth " + oauthParams.map(p => p._1 + "=\"" + percentEncode(p._2) +"\"").mkString(","))
  }
  
  def getSig(baseParams: List[(String,String)], req: Http.Request, consumer: Token, token: Option[Token], verifier: Option[String]): (List[(String,String)], String) = {
    var oauthParams = ("oauth_version", "1.0")::("oauth_consumer_key", consumer.key)::("oauth_signature_method", "HMAC-SHA1") :: baseParams
    
    token.foreach{t =>
      oauthParams ::= ("oauth_token", t.key)
    }
    
    verifier.foreach{v =>
      oauthParams ::= ("oauth_verifier", v)
    }
    
    val baseString = List(req.method.toUpperCase,normalizeUrl(req.url(req)),normalizeParams(req.params ++ oauthParams)).map(percentEncode).mkString("&")
    
    val keyString = percentEncode(consumer.secret) + "&" + token.map(t => percentEncode(t.secret)).getOrElse("")
    val key = new SecretKeySpec(keyString.getBytes(Http.utf8), MAC)
    val mac = Mac.getInstance(MAC)
    mac.init(key)
    val text = baseString.getBytes(Http.utf8)
    (oauthParams, Http.base64(mac.doFinal(text)))
  }
  
  private def normalizeParams(params: List[(String,String)]) = {
    percentEncode(params).sortWith(_ < _).mkString("&")
  }
  
  private def normalizeUrl(url: URL) = {
    val uri = new URI(url.toString)
    val scheme = uri.getScheme().toLowerCase()
    var authority = uri.getAuthority().toLowerCase()
    val dropPort = (scheme.equals("http") && uri.getPort() == 80) || (scheme.equals("https") && uri.getPort() == 443)
    if (dropPort) {
      // find the last : in the authority
      val index = authority.lastIndexOf(":")
      if (index >= 0) {
        authority = authority.substring(0, index)
      }
    }
    var path = uri.getRawPath()
    if (path == null || path.length() <= 0) {
      path = "/" // conforms to RFC 2616 section 3.2.2
    }
    // we know that there is no query and no fragment here.
    scheme + "://" + authority + path
  }
  
  def percentEncode(params: List[(String,String)]):List[String] = {
    params.map(p => percentEncode(p._1) + "=" + percentEncode(p._2))
  }
  
  def percentEncode(s: String): String = {
    if (s == null) "" else {
       Http.urlEncode(s, Http.utf8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
     }
  }
}