package com.subgen.nativeapp

/** Reviews are evidence-bound snapshots, not mutable "approved" booleans. */
object HyperGovernance {
    data class Identity(val scriptHash:String,val rulesHash:String,val rulesVersion:String,val mode:String,
        val cues:String,val acousticSettings:String,val renderConfig:String,val audioHash:String="",val renderId:String="",val subgenVersion:String=HyperAnalysis.VERSION) {
        val textKey get()=HyperAnalysis.hash(listOf(scriptHash,rulesHash,rulesVersion,mode,cues,acousticSettings,subgenVersion).joinToString("\u0000"))
        val audioKey get()=HyperAnalysis.hash(listOf(textKey,renderConfig,audioHash,renderId).joinToString("\u0000"))
    }
    data class Review(val scope:String,val status:String,val binding:String,val note:String,val time:Long,
        val rule:String="",val loopListened:Boolean=false,val renderId:String="")
    data class State(val technical:String,val editorial:String,val audio:String,val listener:String,val release:String)
    fun state(id:Identity,reviews:List<Review>,errors:Int,warnings:Int,loop:Boolean):State {
        fun accepted(scope:String,binding:String)=reviews.lastOrNull{it.scope==scope&&it.binding==binding}?.status=="ACCEPTED"
        val text=accepted("TEXT",id.textKey)
        val audioReview=reviews.lastOrNull{it.scope=="AUDIO"&&it.binding==id.audioKey}
        val audio=errors==0&&id.audioHash.isNotEmpty()&&id.renderId.isNotEmpty()&&audioReview?.status=="ACCEPTED"&&(!loop||audioReview.loopListened)
        val listener=audio&&accepted("LISTENER",id.audioKey)
        val release=errors==0&&text&&audio&&accepted("RELEASE",id.audioKey)
        return State(if(errors>0)"VALIDATION FAILED" else if(warnings>0)"WARNINGS PRESENT" else "DRAFT",
            if(text&&errors==0)"TEXT APPROVED" else "DRAFT",if(audio)"AUDIO VERIFIED" else "AUDIO PENDING",
            if(listener)"LISTENER EVALUATED" else "PENDING",if(release)"RELEASE APPROVED" else "PENDING")
    }
    fun approve(scope:String,id:Identity,reviews:List<Review>,errors:Int,warnings:Int,loop:Boolean,note:String,
        loopListened:Boolean=false,time:Long=System.currentTimeMillis()):Review {
        require(errors==0){"Technical parser/render errors cannot be overridden by editorial approval"}
        require(note.isNotBlank()){"Record reviewer notes"}
        val state=state(id,reviews,errors,warnings,loop)
        if(scope in setOf("AUDIO","LISTENER","RELEASE"))require(id.audioHash.isNotEmpty()&&id.renderId.isNotEmpty()){"A completed, identified WAV render is required"}
        if(scope=="AUDIO"&&loop)require(loopListened){"Loop-junction listening must be recorded"}
        if(scope=="LISTENER")require(state.audio=="AUDIO VERIFIED"){"Complete audio review first"}
        if(scope=="RELEASE")require(state.editorial=="TEXT APPROVED"&&state.audio=="AUDIO VERIFIED"){"Text and audio approval are required"}
        require(scope in setOf("TEXT","AUDIO","LISTENER","RELEASE"))
        return Review(scope,"ACCEPTED",if(scope=="TEXT")id.textKey else id.audioKey,note,time,loopListened=loopListened,renderId=id.renderId)
    }
    fun exception(finding:HyperAnalysis.Finding,id:Identity,reason:String):Review {
        require(finding.severity!="ERROR"&&finding.allowException){"This rule cannot be excepted"}
        require(reason.isNotBlank()){"An exception needs a reason"}
        return Review("EXCEPTION","EXCEPTION APPROVED",id.textKey,reason,System.currentTimeMillis(),finding.rule)
    }
}
