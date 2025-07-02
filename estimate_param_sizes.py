import os
import xml.etree.ElementTree as ET
import re
import csv
import subprocess
from pathlib import Path

# === CONFIGURATION ===
project_name = "jpetstore"
PROJECT_DIR = Path(f"./projects/{project_name}")
XML_INPUT_PATH = PROJECT_DIR / f"{project_name}.xml"
TYPE_INPUT_FILE = PROJECT_DIR / "type_list.txt"
TYPE_OUTPUT_FILE = PROJECT_DIR / "type_size_output.csv"
METHOD_SIZE_OUTPUT_FILE = PROJECT_DIR / f"method_parameters_sizeOf.csv"

# === Java Agent + Classpath Setup ===
AGENT_CLASSES = "agent"
PROJECT_CLASSES = PROJECT_DIR / f"target/{project_name}-classes.jar"
PROJECT_LIB_DIR = PROJECT_DIR / f"target/{project_name}-war-extracted/WEB-INF/lib"
AGENT_JAR_PATH = "agent/agent.jar"

# Gather all JARs from WEB-INF/lib
war_jars = [
    str(Path(PROJECT_LIB_DIR) / jar)
    for jar in os.listdir(PROJECT_LIB_DIR)
    if jar.endswith(".jar")
]

# Full classpath
if PROJECT_CLASSES.exists():
    classpath = f"{AGENT_CLASSES}:{PROJECT_CLASSES}:{':'.join(war_jars)}"
else:
    classpath = f"{AGENT_CLASSES}:{':'.join(war_jars)}"

JAVA_CMD = [
    "java",
    f"-javaagent:{AGENT_JAR_PATH}",
    "-cp", classpath,
    "SizeTester"
]

DEFAULT_SIZE = 64  # Fallback size if type has no size entry

# === Step 1: Parse method signatures from deps.xml ===
def parse_methods_from_deps(xml_path):
    tree = ET.parse(xml_path)
    root = tree.getroot()
    methods = []
    for feature in root.findall(".//feature"):
        name_elem = feature.find("name")
        if name_elem is not None and name_elem.text:
            method_sig = name_elem.text.strip()
            methods.append(method_sig)
    return methods

# === Step 2: Extract parameter types ===
def extract_param_types(method_sig):
    match = re.search(r"\((.*?)\)", method_sig)
    if match:
        params = match.group(1)
        if not params:
            return []
        return [p.strip() for p in params.split(",")]
    return []

# === Step 3: Write unique types to file for Java agent ===
def write_type_file(unique_types, filepath):
    with open(filepath, "w") as f:
        for typename in sorted(unique_types):
            f.write(typename + "\n")

# === Step 4: Run Java agent to get sizes ===
def run_agent_and_get_sizes(input_file, output_file):
    cmd = JAVA_CMD + [str(input_file), str(output_file)]
    print(f"Running: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)

# === Step 5: Read size output from agent ===
def read_type_sizes(output_file):
    type_size_map = {}
    with open(output_file, newline='') as csvfile:
        for line in csvfile:
            if "," not in line:
                continue
            typename, size_str = line.strip().split(",", 1)
            try:
                type_size_map[typename.strip()] = int(size_str)
            except ValueError:
                type_size_map[typename.strip()] = DEFAULT_SIZE
    return type_size_map

# === Step 6: Compute method parameter sizes and write output ===
def compute_and_save_method_sizes(methods, type_size_map, output_csv):
    rows = []
    for method in methods:
        param_types = extract_param_types(method)
        param_sizes = [type_size_map.get(p, DEFAULT_SIZE) for p in param_types]
        total_size = sum(param_sizes)
        rows.append((method, param_types, param_sizes, total_size))
        
    
    with open(output_csv, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Method", "Parameter Types", "Param Sizes", "Total Param Size"])
        for row in rows:
            writer.writerow(row)

    print(f"Method size table saved to: {output_csv}")

# === Main ===
def main():
    methods = parse_methods_from_deps(XML_INPUT_PATH)
    all_types = set()
    for m in methods:
        all_types.update(extract_param_types(m))

    write_type_file(all_types, TYPE_INPUT_FILE)
    run_agent_and_get_sizes(TYPE_INPUT_FILE, TYPE_OUTPUT_FILE)
    type_size_map = read_type_sizes(TYPE_OUTPUT_FILE)
    compute_and_save_method_sizes(methods, type_size_map, METHOD_SIZE_OUTPUT_FILE)

if __name__ == "__main__":
    main()

# This script estimates the sizes of method parameters in a Java project.
# It extracts method signatures from a given XML file, runs a Java agent to compute sizes,
# and writes the results to a CSV file.