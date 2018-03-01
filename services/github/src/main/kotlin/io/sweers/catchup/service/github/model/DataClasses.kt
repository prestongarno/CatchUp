package io.sweers.catchup.service.github.model

data class PageInfo(val map: Map<String, Any?>) {
  val hasNextPage: Boolean by map
  val endCursor: String? by map
}

data class Repository(val map: Map<String, Any>) {
  val id: String by map
  val url: String by map
  val name: String by map
  val createdAt: String by map
  val description: String by map
  @Suppress("UNCHECKED_CAST")
  val languages = (map.obj("languages")!!["nodes"] as? Iterable<Map<String, Any>>)?.firstOrNull()?.get("name")?.toString()!!
  val licenseInfo = map.obj("licenseInfo")!!["name"]!!.toString()
  val owner= map.obj("owner")!!["name"]!!.toString()
  val stargazers = map.obj("stargazers")!!["totalCount"]!!.toString().toIntOrNull() ?: 0
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.obj(name: String) = this[name] as? Map<String, Any>
