package the.bytecode.club.bytecodeviewer.util.apk2Jar;

import hu.oandras.kJarify.dex.DexProcessor;
import hu.oandras.kJarify.dex.DexReader;
import hu.oandras.kJarify.jvm.optimization.OptimizationOptions;
import org.jetbrains.annotations.NotNull;
import the.bytecode.club.bytecodeviewer.resources.ResourceContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;

public class kJarify extends Apk2Jar
{

    private static final OptimizationOptions optimizationOptions = OptimizationOptions.PRETTY;

    private ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @Override
    protected ResourceContainer resourceContainerFromApkImpl(File inputApk)
    {
        ExecutorService executorService = this.createExecutorService();

        try
        {
            DexProcessor processor = new DexProcessor(
                optimizationOptions,
                executorService,
                new DexProcessorCallback()
            );

            processor.process(
                DexReader.ApkDexFileReader.INSTANCE.read(inputApk.getAbsolutePath())
            );

            printResultMap("Warning in class ", processor.getErrors());
            printResultMap("Error in class ", processor.getErrors());

            return createResourceContainerFromMap(processor.getClasses());
        }
        finally
        {
            executorService.shutdown();
        }
    }

    private void printResultMap(String prefix, Map<String, String> result)
    {
        for (Map.Entry<String, String> error : result.entrySet())
        {
            System.out.println(prefix + error.getKey() + ": " + error.getValue());
        }
    }

    @Override
    protected void apk2FolderImpl(File input, File output)
    {
        ExecutorService executorService = this.createExecutorService();

        try
        {
            DexProcessor processor = new DexProcessor(
                optimizationOptions,
                executorService,
                new DexProcessorCallback() {

                    private void ensureParentFolder(File file) {
                        File parentFile = file.getParentFile();
                        if (!parentFile.mkdirs()) {
                            throw new RuntimeException("Unable to create parent folder: " + parentFile.getAbsolutePath());
                        }
                    }

                    @Override
                    public void onClassTranslated(@NotNull String unicodeRelativePath, byte[] classData)
                    {
                        File outFile = new File(output, unicodeRelativePath);
                        ensureParentFolder(outFile);
                        try (OutputStream outputStream = new FileOutputStream(outFile))
                        {
                            outputStream.write(classData);
                        } catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            );

            processor.process(
                DexReader.ApkDexFileReader.INSTANCE.read(input.getAbsolutePath())
            );

            printResultMap("Warning in class ", processor.getErrors());
            printResultMap("Error in class ", processor.getErrors());
        }
        finally
        {
            executorService.shutdown();
        }
    }

    protected void apk2JarImpl(File input, File output)
    {
        hu.oandras.kJarify.KJarify.process(input, output, OptimizationOptions.PRETTY, Deflater.BEST_SPEED);
    }

    private static class DexProcessorCallback extends DexProcessor.ProcessCallBack
    {

        @Override
        public void onProgress(int translated, int warnings, int errors, int total)
        {
            System.out.println(
                "Processing... " + translated + " classes processed, error count: " + errors +
                    ", warning count: " + warnings + ", total: " + total
            );
        }
    }
}
