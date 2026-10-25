import com.subgen.nativeapp.WorkerPolicy

fun main() {
    check(WorkerPolicy.choose(8,0,4000,0)==4)
    check(WorkerPolicy.choose(8,0,4000,2)==2)
    check(WorkerPolicy.choose(8,0,4000,4)==1)
    check(WorkerPolicy.choose(8,4,900,0)==2)
    check(WorkerPolicy.choose(8,4,400,0)==1)
    check(WorkerPolicy.choose(4,0,4000,0)==2)
    check(WorkerPolicy.choose(2,0,4000,0)==1)
    check(WorkerPolicy.choose(8,3,4000,0)==3)
    check(WorkerPolicy.choose(8,12,4000,0)==4)
    check(WorkerPolicy.winner(listOf(2 to 3.0,4 to 2.0))?.first==4)
    check(WorkerPolicy.winner(listOf(2 to 2.0,4 to 1.92))?.first==2)
    check(WorkerPolicy.winner(listOf(2 to 5.0,4 to Double.NaN))?.first==2)
    check(WorkerPolicy.winner(listOf(2 to -5.0,4 to Double.POSITIVE_INFINITY))==null)
    println("PASS: adaptive CPU defaults, calibration, thermal limits, limited memory, benchmark outliers")
}
