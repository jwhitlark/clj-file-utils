(ns clj-file-utils.core
  (:require [clojure.contrib.duck-streams :as streams])
  (:require [clojure.contrib.io :as io])
  (:use [clojure.contrib.shell-out :only (sh)])
  (:import [java.io File])
  (:gen-class))

; The following function is retained for backwards compatibility purposes.
(def file io/file)

; The following function is retained for backwards compatibility purposes.
(def pwd io/pwd)

; The following function is retained for backwards compatibility purposes.
(def cwd io/pwd)

(defmacro defun [name docstring args & body]
  `(do
     (defmulti ~name ~docstring class)
     (defmethod ~name File ~args ~@body)
     (defmethod ~name String ~args (~name (io/file ~@args)))
     (defmethod ~name :default ~args false)))

(defun file?
  "Returns true if the path is a file; false otherwise."
  [path]
  (.isFile path))

(defun directory?
  "Returns true if the path is a directory; false otherwise."
  [path]
  (.isDirectory path))

(defun exists?
  "Returns true if path exists; false otherwise."
  [path]
  (.exists path))

(defn ls
  "List files in a directory."
  [dir]
  (seq (.listFiles (io/file dir))))

(defn touch
  "Create a file or update the last modified time."
  [path]
  (let [file (io/file path)]
    (do
      (.createNewFile file)
      (.setLastModified file (System/currentTimeMillis)))))

(defn mkdir
  "Create a directory."
  [dir]
  (.mkdir (io/file dir)))

(defn mkdir-p
  "Create a directory and all parent directories if they do not exist."
  [dir]
  (.mkdirs (io/file dir)))

(defn canonical-path
  "Returns the canonical path of the file or directory."
  [path]
  (.getCanonicalPath (io/file path)))

(defn size
  "Returns the size in bytes of a file."
  [file]
  (.length (io/file file)))

(defn rm
  "Remove a file. Will throw an exception if the file cannot be deleted."
  [file]
  (io/delete-file file))

(defn rm-f
  "Remove a file, ignoring any errors."
  [file]
  (io/delete-file file true))

(defn rm-r
  "Remove a directory. The directory must be empty; will throw an exception
    if it is not or if the file cannot be deleted."
  [path]
  (io/delete-file-recursively path))

(defn rm-rf
  "Remove a directory, ignoring any errors."
  [path]
  (io/delete-file-recursively path true))

(defn cp
  "Copy a file, preserving last modified time by default."
  [from to & {:keys [preserve] :or {preserve true}}]
  (let [from-file (io/file from)
        to-file (io/file to)]
    (do
      (streams/copy from-file to-file)
      (if preserve
        (.setLastModified to-file (.lastModified from-file))))))

(defn cp-r
  "Copy a directory, preserving last modified times by default."
  [from to & {:keys [preserve] :or {preserve true}}]
  (let [from-dir (io/file from)
        to-dir (io/file to)
        cp-directory (fn [a b t preserve]
                       (do
                         (cp-r a b :preserve preserve)
                         (if preserve
                           (.setLastModified b t))))]
    (do
      (mkdir-p to-dir)
      (doseq [path (ls from-dir)]
        (let [mod-time (.lastModified path)
              copied-path (io/file to-dir (.getName path))]
          (cond
            (file? path) (cp path copied-path :preserve preserve)
            (directory? path) (cp-directory path
                                            copied-path
                                            mod-time
                                            preserve)))))))

(defn mv
  "Try to rename a file, or copy and delete if on another filesystem."
  [from to]
  (let [from-file (io/file from)
        to-file (io/file to)]
    (if (not (.renameTo from-file to-file))
      (do
        (cp from-file to-file)
        (rm from-file)))))

(defn chmod
  "Changes file permissions (UNIX only); for portability, consider pchmod."
  [args path]
  (sh "chmod" args (.getAbsolutePath (io/file path))))

(defn pchmod
  "Change file permissions in a portable way."
  [path & {:keys [r w x]}]
  (let [file (io/file path)]
    (do
      (if-not (nil? r) (.setReadable file r))
      (if-not (nil? w) (.setWritable file w))
      (if-not (nil? x) (.setExecutable file x)))))
