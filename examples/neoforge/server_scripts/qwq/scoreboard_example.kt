package qwq

import top.katton.api.ServerScriptEntrypoint
import top.katton.api.dpcaller.getOrCreateObjective
import top.katton.api.dpcaller.setScore

@ServerScriptEntrypoint
fun scoreboardExampleMain(){
    //get the scoreboard objective
    val obj = getOrCreateObjective("myscore")
    //set the score of "test" to 100
    setScore("test", obj, 100)
}