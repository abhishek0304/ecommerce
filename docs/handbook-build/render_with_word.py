import importlib.util, os
from pathlib import Path
build=Path(__file__).parent
skill=Path('C:/Users/Abhishek Singh/.codex/plugins/cache/openai-primary-runtime/documents/26.904.11930/skills/documents/render_docx.py')
os.environ['PATH']='C:/Users/Abhishek Singh/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin'+os.pathsep+os.environ['PATH']
spec=importlib.util.spec_from_file_location('packaged_renderer',skill)
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.convert_to_pdf=lambda *args,**kwargs:(str(build/'word-preview.pdf'),'PDF exported using Microsoft Word because LibreOffice is unavailable')
pages=m.rasterize(str(build.parent/'Ecommerce_Application_Architecture_API_and_Interview_Guide.docx'),str(build/'render'),110,False,False)
print('Rendered',len(pages),'pages with packaged rasterizer')
