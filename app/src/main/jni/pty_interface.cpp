#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <dirent.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_verb_terminal_NativeJNI_createPtySubprocess(
        JNIEnv* env,
        jclass clazz,
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars,
        jintArray processIdArray,
        jint rows,
        jint columns,
        jint cellWidth,
        jint cellHeight) {
    int master_fd = -1;
    int slave_fd = -1;

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) columns;
    ws.ws_xpixel = (unsigned short) (columns * cellWidth);
    ws.ws_ypixel = (unsigned short) (rows * cellHeight);

    struct termios tios;
    memset(&tios, 0, sizeof(tios));
    tios.c_iflag = ICRNL | IXON | IUTF8;
    tios.c_oflag = OPOST | ONLCR;
    tios.c_cflag = CS8 | CREAD;
    tios.c_lflag = ISIG | ICANON | ECHO | ECHOE | ECHOK | ECHONL;

    if (openpty(&master_fd, &slave_fd, NULL, &tios, &ws) < 0) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, "openpty() failed to create PTY master/slave pair");
        return -1;
    }

    jsize size = args ? env->GetArrayLength(args) : 0;
    char** argv = nullptr;
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        for (int i = 0; i < size; ++i) {
            jstring arg_str = (jstring) env->GetObjectArrayElement(args, i);
            const char* arg_utf = env->GetStringUTFChars(arg_str, nullptr);
            argv[i] = strdup(arg_utf);
            env->ReleaseStringUTFChars(arg_str, arg_utf);
        }
        argv[size] = nullptr;
    }

    jsize env_size = envVars ? env->GetArrayLength(envVars) : 0;
    char** envp = nullptr;
    if (env_size > 0) {
        envp = (char**) malloc((env_size + 1) * sizeof(char*));
        for (int i = 0; i < env_size; ++i) {
            jstring env_str = (jstring) env->GetObjectArrayElement(envVars, i);
            const char* env_utf = env->GetStringUTFChars(env_str, nullptr);
            envp[i] = strdup(env_utf);
            env->ReleaseStringUTFChars(env_str, env_utf);
        }
        envp[env_size] = nullptr;
    }

    const char* cmd_utf = env->GetStringUTFChars(cmd, nullptr);
    const char* cwd_utf = env->GetStringUTFChars(cwd, nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        close(master_fd);
        close(slave_fd);
        env->ReleaseStringUTFChars(cmd, cmd_utf);
        env->ReleaseStringUTFChars(cwd, cwd_utf);
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, "fork() failed for PTY subprocess");
        return -1;
    } else if (pid == 0) {
        close(master_fd);
        setsid();

        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        if (slave_fd > STDERR_FILENO) {
            close(slave_fd);
        }

        sigset_t sigs;
        sigfillset(&sigs);
        sigprocmask(SIG_UNBLOCK, &sigs, nullptr);

        if (envp) {
            clearenv();
            for (char** e = envp; *e; ++e) {
                putenv(*e);
            }
        }

        if (cwd_utf && strlen(cwd_utf) > 0) {
            chdir(cwd_utf);
        }

        execvp(cmd_utf, argv ? argv : (char* const[]){ (char*)cmd_utf, nullptr });
        _exit(127);
    } else {
        close(slave_fd);

        env->ReleaseStringUTFChars(cmd, cmd_utf);
        env->ReleaseStringUTFChars(cwd, cwd_utf);

        if (argv) {
            for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
            free(argv);
        }
        if (envp) {
            for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
            free(envp);
        }

        jint* pProcId = (jint*) env->GetPrimitiveArrayCritical(processIdArray, nullptr);
        if (pProcId) {
            *pProcId = (jint) pid;
            env->ReleasePrimitiveArrayCritical(processIdArray, pProcId, 0);
        }

        return master_fd;
    }
}

} // extern "C"
