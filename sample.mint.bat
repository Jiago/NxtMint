@REM Start NxtMint

@REM ###################################################
@REM # Rename to mint.bat and make any desired changes #
@REM ###################################################

@echo Starting NxtMint
java -Xmx256m -Djava.library.path="aparapi;jni" -jar NxtMint-1.2.0.jar

