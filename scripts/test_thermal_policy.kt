import com.subgen.nativeapp.ThermalRecheckPolicy
fun main(){
check(ThermalRecheckPolicy.shouldCompare(4,36,3,2200,4.0,false))
check(!ThermalRecheckPolicy.shouldCompare(4,36,3,1200,4.0,false))
check(!ThermalRecheckPolicy.shouldCompare(4,36,4,2200,4.0,false))
check(ThermalRecheckPolicy.preferTwo(4.0,3.0))
check(!ThermalRecheckPolicy.preferTwo(4.0,3.9))
println("PASS: thermally-aware neural worker selection")
}
