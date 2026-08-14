package com.amond.kmpbook.modding.api.content

/**
 * Entry point implemented by separately trusted executable content.
 *
 * The ordinary manifest/JSON mode loader never discovers or invokes this interface. A host must
 * explicitly trust an implementation, create its scoped [GameContentModApi], and invoke it before
 * the instrument catalog and campaign are constructed.
 */
fun interface GameContentMod {
    fun register(api: GameContentModApi)
}
