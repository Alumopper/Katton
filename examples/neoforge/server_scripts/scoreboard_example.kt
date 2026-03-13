import top.katton.api.dpcaller.getObjective
import top.katton.api.dpcaller.getOrCreateObjective
import top.katton.api.dpcaller.setScore

fun scoreboardExampleMain(){
    //get the scoreboard objective
    val obj = getOrCreateObjective("myscore")
    //set the score of "test" to 100
    setScore("test", obj, 100)
}

val scoreboardExampleMain = scoreboardExampleMain()