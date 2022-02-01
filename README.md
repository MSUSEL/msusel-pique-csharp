# msusel-pique-csharp
This repository represents a C# actualization of the [PIQUE](https://github.com/MSUSEL/msusel-pique) quality analysis platform.
This project integrates the C# static analysis framework tool, *Roslynator*, and provides example extensions of the default weighting, benchmarking, normalizing, and evaluation strategies provided by PIQUE.

Additionally, this project provides the test cases and exercises used to verify components of the PIQUE system from the thesis backing this project.  


## Build Environment
- Java 8+
- Maven
- MSBuild
    - Can be obtained from a Visual Studio 2019 install; found in `C:/Program Files (x86)/Microsoft Visual Studio/2019/Community/MSBuild/Current/Bin`.  This is the easiest way to get an MSBuild version that cooperates with the .NET Framework 4.x projects in the C# benchmark repository.
- R 3.6.1 (only needed for model derivation)
  - with library 'jsonlite'
  - The version is necessary to work with jsonlite.  This R dependency current exists for legacy sake, but should be depreciated as soon as possible.

## Building
1. Ensure the [Build Environment](#build-environment) requirements are met.
1. Ensure *msusel-pique* is installed as a resource as described in the [msusel-pique README](https://github.com/MSUSEL/msusel-pique) (using `mvn install` in the *msusel-pique* cloned root directory).
1. Clone repository into `<project_root>` folder
1. Derive the model as defined in the Model Derivation section below
___

## Deployment - Run quality assessment via an OS-independent JAR 
(todo)
- step 1: Download [PiqueCsharp-jar-with-dependencies.jar](https://github.com/MSUSEL/msusel-pique-csharp/blob/main/target/PiqueCsharp-jar-with-dependencies.jar) into a directory that contains the project needed to be analyzed.
- setp 2: Download [pique-properties.properties](https://github.com/MSUSEL/msusel-pique-csharp/blob/main/target/pique-properties.properties) into a directory that contains the project needed to be analyzed.
- step 3: point project.root= to your desired project in pique-properties.properties file
- step 4: Download [pique_csharp_model.json](https://github.com/MSUSEL/msusel-pique-csharp/blob/main/target/pique_csharp_model.json) into a directory that contains the project needed to be analyzed.
- step 5: Download [piqueCsharpBenchmarkRepo](https://github.com/MSUSEL/msusel-pique-csharp/tree/main/src/main/resources/benchmark/Benchmark5Findings)
- step 6: point benchmark.rep= to your piqueCsharpBenchmarkRep in pique-properties.properties file
- you can also ignore step 5 and step 6, directly download the [derieved model](https://github.com/MSUSEL/msusel-pique-csharp/blob/main/out/CSharpQualityModel.json) and then point derived.qm= to [derieved model](https://github.com/MSUSEL/msusel-pique-csharp/blob/main/out/CSharpQualityModel.json) Suggested
- step 7: run java -jar PiqueCsharp-jar-with-dependencies.jar -d to derive a derived model.
- step 8: run java -jar PiqueCsharp-jar-with-dependencies.jar -e to evaluate.
- step 9: find the evaluated result file in /out directory.







